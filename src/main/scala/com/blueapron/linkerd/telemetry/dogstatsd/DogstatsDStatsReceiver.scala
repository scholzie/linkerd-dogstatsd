package com.blueapron.linkerd.telemetry.dogstatsd

import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter, Stat, StatsReceiverWithCumulativeGauges }
import com.twitter.logging.{ Level, Logger }
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._

private[telemetry] object DogstatsDStatsReceiver {
  private val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")
  log.setLevel(Level.DEBUG)

  private[this] val metricNameDisallowedChars = "[^/A-Za-z0-9]"
  private[this] def escapeMetric(name: Seq[String]): String = {
    name.mkString("/")
      .replaceAll(metricNameDisallowedChars, "_")
      .replaceAll("__+", "_")
      .replaceAll("//+", "/")
      .replaceAll("/", ".")
  }
  private[this] val tagKeyDisallowedChars = "[^A-Za-z0-9]".r
  private[this] def escapeTagKey(key: String) = tagKeyDisallowedChars.replaceAllIn(key, "_")
  private[this] val tagValueDisallowedChars = """(\\|\"|\n|\t|\r|,|#)""".r
  private[this] def escapeTagValue(value: String) = tagValueDisallowedChars.replaceAllIn(value, """\\\\""")

  private[this] def formatTags(tags: Seq[(String, String)]): Seq[String] =
    if (tags.nonEmpty) {
      tags.map {
        case (k, v) =>
          s"${escapeTagKey(k)}:${escapeTagValue(v)}"
      }
    } else {
      Seq()
    }

  private[this] val first: ((String, String)) => String = _._1
  private[this] def tagExists(tags: Seq[(String, String)], name: String) =
    tags.toIterator.map(first).contains(name)

  private[dogstatsd] def mkName(
    name: Seq[String],
    tags: Seq[(String, String)] = Nil
  ): (String, Seq[String]) = {
    // Take pieces of the name and turn them into tags
    log.ifDebug(s"""Creating name and tags from name "${name}" and tags "${tags}"""")
    val (cleanName, cleanTags) = name match {
      case Seq("rt", router) if !tagExists(tags, "rt") =>
        (Seq("rt"), tags :+ ("rt" -> router))
      case Seq("rt", "service", path) if !tagExists(tags, "service") =>
        (Seq("rt", "service"), tags :+ ("service" -> path))
      case Seq("rt", "client", id) if !tagExists(tags, "client") =>
        (Seq("rt", "client"), tags :+ ("client" -> id))
      case Seq("rt", "client", "service", path) if !tagExists(tags, "service") =>
        (Seq("rt", "client", "service"), tags :+ ("service" -> path))
      case Seq("rt", "server", srv) if !tagExists(tags, "server") =>
        (Seq("rt", "server"), tags :+ ("server" -> srv))
      case _ => (name, tags)
    }

    val metricName = escapeMetric(cleanName)
    log.debug(s"Final metricName: $metricName, Final tags: $cleanTags")
    log.debug(s"""Fomatted tags: "${formatTags(cleanTags).mkString(",")}"""")
    (metricName, formatTags(cleanTags))
  }
}

private[telemetry] class DogstatsDStatsReceiver(
    dogstatsDClient: StatsDClient,
    sampleRate: Double,
    debug: Boolean = false
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
    val _ = gauges.put(dogstatsDName, new Metric.Gauge(dogstatsDClient, dogstatsDName, f, tags, debug))
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
      tags,
      debug
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
      tags,
      debug
    )
    val stat = stats.putIfAbsent(dogstatsDName, newStat)
    if (stat != null) stat else newStat
  }
}
