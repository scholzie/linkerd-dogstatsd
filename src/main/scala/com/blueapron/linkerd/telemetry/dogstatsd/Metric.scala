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
      tags: Seq[String],
      debug: Boolean = false
  ) extends FCounter {
    def incr(delta: Int): Unit = {
      log.ifDebug("Sending Counter with {name: %s, delta: %s, sampleRate: %s, tags: %s}"
        .format(name, delta, sampleRate, tags))
      if (!debug)
        dogstatsDClient.count(name, delta, sampleRate, tags: _*)
      else
        log.debug("Debug mode enabled - did not sent metric.")
    }
  }

  // gauges simply evaluate on send
  class Gauge(
      dogstatsDClient: StatsDClient,
      name: String,
      f: => Float,
      tags: Seq[String],
      debug: Boolean = false
  ) {
    def send: Unit = {
      log.ifDebug("Sending Gauge with {name: %s, f: %s, tags: %s}"
        .format(name, f, tags))
      if (!debug)
        dogstatsDClient.recordGaugeValue(name, f, tags: _*)
      else
        log.debug("Debug mode enabled - did not sent metric.")
    }
  }

  // stats (timing/histograms) only send when Math.random() <= sampleRate
  class Stat(
      dogstatsDClient: StatsDClient,
      name: String,
      sampleRate: Double,
      tags: Seq[String],
      debug: Boolean = false
  ) extends FStat {
    def add(value: Float): Unit = {
      log.ifDebug("Sending Histogram with {name: %s, value: %s, sampleRate: %s, tags: %s}"
        .format(name, value.toLong, sampleRate, tags))
      if (!debug)
        dogstatsDClient.recordHistogramValue(name, value.toLong, sampleRate, tags: _*)
      else
        log.debug("Debug mode enabled - did not sent metric.")
    }
  }
}
