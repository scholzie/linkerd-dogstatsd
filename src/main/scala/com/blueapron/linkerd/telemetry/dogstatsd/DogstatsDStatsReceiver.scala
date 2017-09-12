package com.blueapron.linkerd.telemetry.dogstatsd

import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter, Stat, StatsReceiverWithCumulativeGauges }
import com.twitter.logging._
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.util.control.Breaks._

private[telemetry] object DogstatsDStatsReceiver {
  // TODO: Work through metric name scenarios for comprehensive tagging and
  //       statistic naming
  private val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")
  log.setLevel(Level.DEBUG)

  private[dogstatsd] def mkDefaultName(name: Seq[String]): (String, Seq[String]) = {
    log.ifDebug("In mkDefaultName() with\n\tname: %s".format(name.mkString("/")))
    val tags = new ListBuffer[String]()
    val nameBuf = StringBuilder.newBuilder
    // nameBuf.append("linkerd.")
    tags += s"app:unknown"
    nameBuf.append(name.mkString("."))
    log.ifDebug("\tFinal nameBuf: %s".format(nameBuf.toString()))
    log.ifDebug("\tFinal tags: %s".format(tags.mkString("; ")))
    log.debug("Exiting mkDefaultName()")
    (nameBuf.toString(), tags.toSeq)
  }

  private[dogstatsd] def mkRouterName(cleanName: Seq[String]): (String, Seq[String]) = {
    log.debug("Entering mkRoutercleanName() with\n\tcleanName: %s".format(cleanName.mkString("/")))
    val metricNameBuf = StringBuilder.newBuilder
    val tags = new ListBuffer[String]()
    metricNameBuf.append("routers")

    val routerLabel = cleanName(2)
    tags += s"router:$routerLabel"
    log.ifDebug(s"\trouterLabel: $routerLabel")

    if (cleanName(3) == "server") {
      log.debug("\tmunging rt.server")
      metricNameBuf.append(".servers")
      val listenerAddress = cleanName(4)
      val listenerPort = cleanName(5)
      tags += s"linkerd_listener:$listenerAddress:$listenerPort"
      log.ifDebug("\tmetricNameBuf: %s".format(metricNameBuf.toString()))
      log.ifDebug("\ttags: %s".format(tags.mkString("; ")))
    } else {
      log.debug("\tmunging rt.[interpreter]")
      metricNameBuf.append(".interpreters")
      val interpreter = cleanName(5)
      tags += s"linkerd_interpreter:$interpreter"
      log.ifDebug("\tmetricNameBuf: %s".format(metricNameBuf.toString()))
      log.ifDebug("\ttags: %s".format(tags.mkString("; ")))
    }
    log.ifDebug("Exiting mkRoutercleanName with metricNameBuf: %s.%s".format(metricNameBuf.toString(), cleanName.last))
    metricNameBuf.append(".%s".format(cleanName.last)) // append the stat itself

    (metricNameBuf.toString(), tags.toSeq)
  }

  private[dogstatsd] def mkName(name: Seq[String]): (String, Seq[String]) = {
    log.ifDebug("Entering mkName() with name: %s".format(name.mkString("/")))
    val metricName = StringBuilder.newBuilder
    val tags = new ListBuffer[String]()
    val cleanName: Seq[String] = name.mkString("/")
      .replaceAll("[^/A-Za-z0-9]", "_")
      .replaceAll("//", "/")
      .replaceAll("/", ".")
      .split("\\.+").toSeq
    log.ifDebug("\tcleanName: %s".format(cleanName.mkString("/")))

    // Find the environment, since we know that services come in the form
    //   `etoy.staging.primary.web`. However. it's worth noting that other
    //   metrics have services in the form of `etoy_staging_primary_web`, so
    //   we'll have to catch this as well eventually instead of just bailing
    //   out.
    val environmentAnchors = List("development", "production", "staging", "util")
    log.ifDebug("\tEnvironment Anchors: %s".format(environmentAnchors))
    var anchorIndex: Int = -1
    log.debug("\tLooping over anchors")
    for (env <- environmentAnchors) {
      anchorIndex = cleanName.indexOf(env)
      log.ifDebug(s"\t\tenv: $env, anchorIndex: $anchorIndex")
      if (anchorIndex >= 0) {
        log.ifDebug(s"\t\tANCHOR FOUND: %s at cleanName(%s): %s".format(env, anchorIndex, cleanName(anchorIndex)))
        //We need to check for '_'s because this is a different format
        if (cleanName(anchorIndex) contains "_") {
          log.debug("\t\tFalse alarm - anchor is part of a seq item. Resetting anchorIndex to -1")
          anchorIndex = -1
        }
        log.ifDebug(s"\t\tanchorIndex before `break`: $anchorIndex")
        break
      }
    }
    if (anchorIndex >= 0) { // We found a service name and parse the tags
      log.ifDebug("\tanchorIndex was not negative. Attempt to parse tags.")
      val appName = cleanName(anchorIndex - 1)
      val appServiceName = cleanName(anchorIndex + 2)
      val envName = cleanName(anchorIndex)
      val namespace = cleanName(anchorIndex + 1)
      log.debug("\tcleanName: %s".format(cleanName))
      log.debug("\tappName: %s, appServiceName: %s, envName: %s, namespace: %s".format(appName, appServiceName, envName, namespace))
      tags += s"app:$appName"
      tags += s"app_namespace:$namespace"
      tags += s"app_service:$appServiceName"
      log.ifDebug("\ttags: %s".format(tags.mkString("; ")))

      metricName.append(cleanName.filterNot(field => field == appName &&
        field == namespace &&
        field == appServiceName &&
        field == envName).mkString("."))
      log.ifDebug("\tmetricName: %s".format(metricName.toString()))
    } else {
      log.debug("\tanchorIndex was negative. Pass throug original string.")
      metricName.append(mkDefaultName(cleanName)._1)
      log.ifDebug("\tmetricName: %s".format(metricName.toString()))
    }
    log.ifDebug("\tmetricName after conditional: %s".format(metricName.toString()))
    if (metricName.isEmpty) {
      log.debug("\tmetricName is empty! Setting a default value.")
      metricName.append(cleanName.mkString("."))
    }
    tags += "test:linkerd"
    log.ifDebug("\tFinal metricName: %s, Final tags: %s".format(metricName.toString(), tags.mkString("; ")))
    (metricName.toString(), tags.toSeq)
  }

  //  private[dogstatsd] def mkName(name: Seq[String]): (String, Seq[String]) = {
  //    var cleanName = name.mkString("/")
  //      .replaceAll("[^/A-Za-z0-9]", "_")
  //      .replaceAll("/{2,}", "/")
  //      .replace("/", ".") // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
  //      .replaceAll("\\.{2,}", ".")
  //      .split(".")
  //
  //      val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")
  //      log.info(cleanName.mkString("."))
  //      if (cleanName.length >= 2) {
  //        cleanName(2) match {
  //          case "namerd" => mkDefaultName(cleanName) // need to properly parse router names first
  //          case "rt"     => mkDefaultName(cleanName) // and namer names as well.
  //          case _        => mkDefaultName(cleanName)
  //        }
  //        } else {
  //          (cleanName.mkString("."), Seq())
  //        }
  //  }
}

private[telemetry] class DogstatsDStatsReceiver(
    dogstatsDClient: StatsDClient,
    sampleRate: Double
) extends StatsReceiverWithCumulativeGauges {
  import DogstatsDStatsReceiver._

  val repr: AnyRef = this

  private[dogstatsd] def flush(): Unit = {
    gauges.values.asScala.foreach(_.send)
  }
  private[dogstatsd] def close(): Unit = dogstatsDClient.stop()

  private[this] val counters = new ConcurrentHashMap[String, Metric.Counter]
  private[this] val gauges = new ConcurrentHashMap[String, Metric.Gauge]
  private[this] val stats = new ConcurrentHashMap[String, Metric.Stat]

  protected[this] def registerGauge(
    name: Seq[String],
    f: => Float
  ): Unit = {
    deregisterGauge(name)

    val (dogstatsDName, tags) = mkName(name)
    val _ = gauges.put(dogstatsDName, new Metric.Gauge(dogstatsDClient, dogstatsDName, f, tags))
  }

  protected[this] def deregisterGauge(name: Seq[String]): Unit = {
    val _ = gauges.remove(mkName(name)._1)
  }

  def counter(name: String*): Counter = {
    val (dogstatsDName, tags) = mkName(name)
    val newCounter = new Metric.Counter(
      dogstatsDClient,
      dogstatsDName,
      sampleRate,
      tags
    )
    val counter = counters.putIfAbsent(dogstatsDName, newCounter)
    if (counter != null) counter else newCounter
  }

  def stat(name: String*): Stat = {
    val (dogstatsDName, tags) = mkName(name)
    val newStat = new Metric.Stat(
      dogstatsDClient,
      dogstatsDName,
      sampleRate,
      tags
    )
    val stat = stats.putIfAbsent(dogstatsDName, newStat)
    if (stat != null) stat else newStat
  }
}
