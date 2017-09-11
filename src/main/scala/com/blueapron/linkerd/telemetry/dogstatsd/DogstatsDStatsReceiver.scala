package com.blueapron.linkerd.telemetry.dogstatsd

import java.util.concurrent.ConcurrentHashMap
import scala.collection.mutable.ListBuffer
import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter, Stat, StatsReceiverWithCumulativeGauges }
import scala.collection.JavaConverters._
import scala.util.control.Breaks._

private[telemetry] object DogstatsDStatsReceiver {
  // TODO: Work through metric name scenarios for comprehensive tagging and
  //       statistic naming
  private[dogstatsd] def mkDefaultName(name: Seq[String]): (String, Seq[String]) = {
    val tags = new ListBuffer[String]()
    val nameBuf = StringBuilder.newBuilder
    tags += s"app:unknown"
    (nameBuf.toString, tags.toSeq)
  }

  //  private[dogstatsd] def mkRouterName(name: Seq[String]): (String, Seq[String]) = {
  //    val nameBuf = StringBuilder.newBuilder
  //    val tags = new ListBuilder[String]()
  //    nameBuf.append("linkerd.routers")
  //
  //    val routerLabel = name(3)
  //    tags += s"souter:$routerLabel"
  //
  //    if name(5) == "server" {
  //      nameBuf.append(".servers")
  //      val listenerAddress = name(6)
  //      val listenerPort = name(7)
  //      tags += s"linkerd_listener:$serverAddress:$serverPort"
  //    } else {
  //      nameBuf.append(".interpreter")
  //      val interpreter = cleanName(5)
  //      tags += s"linkerd_interpreter:$interpreter"
  //    }
  //    nameBuf.append(name.last) // append the stat itself
  //  }

  private[dogstatsd] def mkName(name: Seq[String]): (String, Seq[String]) = {
    var metricName: String = ""
    val tags = new ListBuffer[String]()
    val cleanName = name.mkString("/")
      .replaceAll("[^/A-Za-z0-9]", "_")
      .replaceAll("/{2,}", "/")
      .replace("/", ".") // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
      .replaceAll("\\.{2,}", ".") // remove all induced "..", "...", etc.
      .split(".")

    // Find the environment, since we know that services come in the form
    //   `etoy.staging.primary.web`. However. it's worth noting that other
    //   metrics have services in the form of `etoy_staging_primary_web`, so
    //   we'll have to catch this as well eventually instead of just bailing
    //   out.
    val environmentAnchors = List("development", "production", "staging", "util")
    var anchorIndex: Int = -1
    for (env <- environmentAnchors) {
      anchorIndex = name.indexOf(env)
      if (anchorIndex >= 0) {
        //We need to check for '_'s because this is a different format
        if (name(anchorIndex) contains "_") anchorIndex = -1
        break
      }
    }
    if (anchorIndex != -1) { // We found a service name and parse the tags
      val envName = cleanName(anchorIndex)
      val appName = cleanName(anchorIndex - 1)
      val namespace = cleanName(anchorIndex + 1)
      val appServiceName = cleanName(anchorIndex + 2)

      tags += s"app:$appName"
      tags += s"app_namespace:$namespace"
      tags += s"app_service:$appServiceName"
      val metricName: String = name.mkString(".")
        .filterNot(field => field == appName &&
          field == namespace &&
          field == appServiceName &&
          field == envName)
    } else {
      mkDefaultName(cleanName)
    }
    if (metricName.isEmpty) metricName = cleanName.mkString(".")
    (metricName, tags)
  }

  //  private[dogstatsd] def mkName(name: Seq[String]): (String, Seq[String]) = {
  //    var cleanName = name.mkString("/")
  //      .replaceAll("[^/A-Za-z0-9]", "_")
  //      .replaceAll("/{2,}", "/")
  //      .replace("/", ".") // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
  //      .replaceAll("\\.{2,}", ".")
  //      .split(".")
  //
  //    cleanName(2) match {
  //        case "namerd" => mkDefaultName(cleanName) // need to properly parse router names first
  //        case "rt"     => mkDefaultName(cleanName) // and namer names as well.
  //        case _        => mkDefaultName(cleanName)
  //    }
  //    val metricName, tags = parseName(cleanName)
  //
  //    (metricName, tags)
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
