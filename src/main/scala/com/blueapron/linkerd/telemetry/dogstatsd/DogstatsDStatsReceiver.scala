package com.blueapron.linkerd.telemetry.dogstatsd

import java.util.concurrent.ConcurrentHashMap
import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter, Stat, StatsReceiverWithCumulativeGauges }
import scala.collection.JavaConverters._

private[telemetry] object DogstatsDStatsReceiver {
  // from https://github.com/researchgate/diamond-linkerd-collector/
  /*** TODO: Determine a way to extract the tag(s) from this name string?? ***/
  private[dogstatsd] def mkName(name: Seq[String]): (String, Seq[String]) = {
    (
      name.mkString("/")
      .replaceAll("[^/A-Za-z0-9]", "_")
      .replace("//", "/")
      .replace("/", "."), // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
      Seq() // <-- tags go here (app, namer, etc.)
    )
  }
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
