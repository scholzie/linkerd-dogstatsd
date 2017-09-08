package com.blueapron.linkerd.telemetry.dogstatsd

import com.timgroup.statsd.StatsDClient
import com.twitter.finagle.stats.{ Counter => FCounter, Stat => FStat }

private[dogstatsd] object Metric {

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Counter(
      dogstatsDClient: StatsDClient,
      name: String,
      sampleRate: Double,
      tags: Seq[String]
  ) extends FCounter {
    def incr(delta: Int): Unit = dogstatsDClient.count(name, delta, sampleRate, tags: _*)
  }

  // gauges simply evaluate on send
  class Gauge(
    dogstatsDClient: StatsDClient,
      name: String,
      f: => Float,
      tags: Seq[String]
  ) {
    def send: Unit = dogstatsDClient.recordGaugeValue(name, f, tags: _*)
  }

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Stat(
    dogstatsDClient: StatsDClient,
      name: String,
      sampleRate: Double,
      tags: Seq[String]
  ) extends FStat {
    def add(value: Float): Unit =
      dogstatsDClient.recordHistogramValue(name, value.toLong, sampleRate, tags: _*)
  }
}
