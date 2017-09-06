package com.blueapron.linkerd.telemetry.dogstatsd

import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{Counter => FCounter, Stat => FStat}

private[statsd] object Metric {

  /*** TODO: How? ***/
  val datadogEnabled = Null
  
  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Counter(statsDClient: StatsDClient, name: String, sampleRate: Double, tags: List[String] = Nil) extends FCounter {
    def incr(delta: Int): Unit = 
    if (!tags.isEmpty && datadogEnabled)
      statsDClient.count(name, delta, sampleRate, tags)
    else 
      statsDClient.count(name, delta, sampleRate)
  }

  // gauges simply evaluate on send
  class Gauge(statsDClient: StatsDClient, name: String, f: => Float, tags: List[String] = Nil) {
    def send: Unit = 
    if (!tags.isEmpty && datadogEnabled)
      statsDClient.recordGaugeValue(name, f, tags)
    else
      statsDClient.recordGaugeValue(name, f)
  }

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Stat(statsDClient: StatsDClient, name: String, sampleRate: Double, tags: List[String] = Nil) extends FStat {
    def add(value: Float): Unit =
      if (datadogEnabled) {
        if (tags.isEmpty) {
          statsDClient.recordHistogramValue(name, value.toLong, sampleRate, tags)
        } else {
          statsDClient.recordHistogramValue(name, value.toLong, sampleRate)
        }
      } else {
        // would prefer `recordHistogramValue`, but that's an extension supported by Datadog and InfluxDB
        statsDClient.recordExecutionTime(name, value.toLong, sampleRate)
      }
  }
}
