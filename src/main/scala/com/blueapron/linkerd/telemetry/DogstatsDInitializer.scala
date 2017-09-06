package com.blueapron.linkerd.telemetry

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.timgroup.statsd.NonBlockingStatsDClient
import com.twitter.finagle.Stack
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.blueapron.linkerd.telemetry.dogstatsd.{DogdogstatsDStatsReceiver, DogdogstatsDTelemeter}

class StatsDInitializer extends TelemeterInitializer {
  type Config = DogdogstatsDConfig
  val configClass = classOf[DogdogstatsDConfig]
  override val configId = "com.blueapron.linkerd.telemetry.dogstatsd"
}

private[telemetry] object DogdogstatsDConfig {
  val DefaultPrefix = "linkerd"
  val DefaultHostname = "127.0.0.1"
  val DefaultPort = 8125
  val DefaultGaugeIntervalMs = 10000 // for gauges
  val DefaultSampleRate = 0.01d // for counters and timing/histograms
  val DefaultConstantTags = List()

  val MaxQueueSize = 10000
}

case class DogdogstatsDConfig(
  prefix: Option[String],
  hostname: Option[String],
  port: Option[Int],
  gaugeIntervalMs: Option[Int],
  constantTags: Option[String],
  @JsonDeserialize(contentAs = classOf[java.lang.Double]) sampleRate: Option[Double]
) extends TelemeterConfig {
  import DogdogstatsDConfig._

  @JsonIgnore override val experimentalRequired = true

  @JsonIgnore private[this] val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")

  @JsonIgnore private[this] val dogstatsDPrefix = prefix.getOrElse(DefaultPrefix)
  @JsonIgnore private[this] val dogstatsDHost = hostname.getOrElse(DefaultHostname)
  @JsonIgnore private[this] val dogstatsDPort = port.getOrElse(DefaultPort)
  @JsonIgnore private[this] val dogstatsDInterval = gaugeIntervalMs.getOrElse(DefaultGaugeIntervalMs)
  @JsonIgnore private[this] val dogstatsDSampleRate = sampleRate.getOrElse(DefaultSampleRate)
  @JsonIgnore private[this] val dogstatsDConstantTags = constantTags.getOrElse(DefaultConstantTags)

  @JsonIgnore
  def mk(params: Stack.Params): DogdogstatsDTelemeter = {
    // initiate a UDP connection at startup time
    log.info("connecting to DogdogstatsD at %s:%d as %s", dogstatsDHost, dogstatsDPort, dogstatsDPrefix)
    val dogstatsDClient = new NonBlockingStatsDClient(
      dogstatsDPrefix,
      dogstatsDHost,
      dogstatsDPort,
      MaxQueueSize,
      dogstatsDConstantTags
    )

    new DogtatsDTelemeter(
      new DogstatsDStatsReceiver(dogstatsDClient, dogstatsDSampleRate),
      dogstatsDInterval,
      DefaultTimer
    )
  }
}
