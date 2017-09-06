package com.blueapron.linkerd.telemetry.dogstatsd

import java.util.concurrent.ConcurrentHashMap
import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{Counter, Stat, StatsReceiverWithCumulativeGauges}
import scala.collection.JavaConverters._

private[telemetry] object StatsDStatsReceiver {
  // from https://github.com/researchgate/diamond-linkerd-collector/
  /*** TODO: Determine a way to extract the tag(s) from this name string?? ***/
  private[statsd] def mkName(name: Seq[String]): String = {
    name.mkString("/")
      .replaceAll("[^/A-Za-z0-9]", "_")
      .replace("//", "/")
      .replace("/", ".") // http://graphite.readthedocs.io/en/latest/feeding-carbon.html#step-1-plan-a-naming-hierarchy
  }
}

private[telemetry] class StatsDStatsReceiver(
  statsDClient: StatsDClient,
  sampleRate: Double
) extends StatsReceiverWithCumulativeGauges {
  import StatsDStatsReceiver._

  val repr: AnyRef = this

  private[statsd] def flush(): Unit = {
    gauges.values.asScala.foreach(_.send)
  }
  private[statsd] def close(): Unit = statsDClient.stop()

  private[this] val counters = new ConcurrentHashMap[String, Metric.Counter]
  private[this] val gauges = new ConcurrentHashMap[String, Metric.Gauge]
  private[this] val stats = new ConcurrentHashMap[String, Metric.Stat]

  protected[this] def registerGauge(name: Seq[String],
    f: => Float, tags: List[String]): Unit = {
    deregisterGauge(name)

    val statsDName = mkName(name)
    val _ = if (!tags.isEmpty && datadogEnabled) 
      gauges.put(statsDName,
        new Metric.Gauge(statsDClient, statsDName, f, tags))
    else
      gauges.put(statsDName, new Metric.Gauge(statsDClient, statsDName, f))
  }

  protected[this] def deregisterGauge(name: Seq[String]): Unit = {
    val _ = gauges.remove(mkName(name))
  }

  def counter(name: String*, tags: List[String] = Nil): Counter = {
    val statsDName = mkName(name)
    val newCounter = if (!tags.isEmpty && datadogEnabled) new Metric.Counter(
      statsDClient,
      statsDName,
      sampleRate)
    else new Metric.Counter(
      statsDClient,
      statsDName,
      sampleRate,
      tags)
    val counter = counters.putIfAbsent(statsDName, newCounter)
    if (counter != null) counter else newCounter
  }

  def stat(name: String*, tags: List[string]): Stat = {
    val statsDName = mkName(name)
    val newStat = if (!tags.empty && datadogEnabled) new Metric.Stat(
      statsDClient,
      statsDName,
      sampleRate,
      tags)
    else new Metric.Stat(
      statsDClient,
      statsDName,
      sampleRate
    )
    val stat = stats.putIfAbsent(statsDName, newStat)
    if (stat != null) stat else newStat
  }
}
