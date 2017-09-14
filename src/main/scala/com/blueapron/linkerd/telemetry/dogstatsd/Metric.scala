package com.blueapron.linkerd.telemetry.dogstatsd

import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter => FCounter, Stat => FStat }
import com.twitter.logging.{ Level, Logger }

private[dogstatsd] object Metric {
  private val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")
  log.setLevel(Level.DEBUG)

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Counter(
      dogstatsDClient: StatsDClient,
      name: String,
      sampleRate: Double,
      tags: Seq[String]
  ) extends FCounter {
    def incr(delta: Int): Unit = {
      log.ifDebug("Sending Counter with {\n\tname: %s, \n\tdelta: %s,\n\tsampleRate: %s,\n\ttags: %s\n}"
        .format(name, delta, sampleRate, tags))
      //dogstatsDClient.count(name, delta, sampleRate, tags: _*)
    }
  }

  // gauges simply evaluate on send
  class Gauge(
      dogstatsDClient: StatsDClient,
      name: String,
      f: => Float,
      tags: Seq[String]
  ) {
    def send: Unit = {
      log.ifDebug("Sending Gauge with {\n\tname: %s, \n\tf: %s,\n\ttags: %s\n}"
        .format(name, f, tags))
      //dogstatsDClient.recordGaugeValue(name, f, tags: _*)
    }
  }

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Stat(
      dogstatsDClient: StatsDClient,
      name: String,
      sampleRate: Double,
      tags: Seq[String]
  ) extends FStat {
    def add(value: Float): Unit = {
      log.ifDebug("Sending Histogram with {\n\tname: %s, \n\tvalue: %s,\n\tsampleRate: %s,\n\ttags: %s\n}"
        .format(name, value.toLong, sampleRate, tags))
      //dogstatsDClient.recordHistogramValue(name, value.toLong, sampleRate, tags: _*)
    }
  }
}
