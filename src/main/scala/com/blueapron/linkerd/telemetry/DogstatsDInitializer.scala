package com.blueapron.linkerd.telemetry

import io.buoyant.telemetry.{ TelemeterInitializer, TelemeterConfig }
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.timgroup.statsd.NonBlockingStatsDClient
import com.twitter.finagle.Stack
import com.twitter.finagle.util.DefaultTimer
import com.twitter.logging.Logger
import com.blueapron.linkerd.telemetry.dogstatsd.{ DogstatsDStatsReceiver, DogstatsDTelemeter }

class DogstatsDInitializer extends TelemeterInitializer {
  type Config = DogstatsDConfig
  override val configClass = classOf[DogstatsDConfig]
  override val configId = "com.blueapron.linkerd.telemetry.dogstatsd"
}

private[telemetry] object DogstatsDConfig {
  val DefaultConstantTags = Seq()
  val DefaultDebugMode = false // only output logs - don't ship to datadog
  val DefaultGaugeIntervalMs = 10000 // for gauges
  val DefaultHostname = "127.0.0.1"
  val DefaultPort = 8125
  val DefaultPrefix = "linkerd"
  val DefaultSampleRate = 0.01d // for counters and timing/histograms
  val MaxQueueSize = 10000
}

case class DogstatsDConfig(
    prefix: Option[String],
    hostname: Option[String],
    port: Option[Int],
    gaugeIntervalMs: Option[Int],
    @JsonDeserialize(contentAs = classOf[java.lang.Double]) sampleRate: Option[Double],
    debug: Option[Boolean],
    constantTags: Option[Seq[String]]
) extends TelemeterConfig {
  import DogstatsDConfig._

  @JsonIgnore override val experimentalRequired = true

  @JsonIgnore private[this] val log = Logger.get("com.blueapron.linkerd.telemetry.dogstatsd")

  @JsonIgnore private[this] val dogstatsDConstantTags = constantTags.getOrElse(DefaultConstantTags)
  @JsonIgnore private[this] val dogstatsDHost = hostname.getOrElse(DefaultHostname)
  @JsonIgnore private[this] val dogstatsDInterval = gaugeIntervalMs.getOrElse(DefaultGaugeIntervalMs)
  @JsonIgnore private[this] val dogstatsDPort = port.getOrElse(DefaultPort)
  @JsonIgnore private[this] val dogstatsDPrefix = prefix.getOrElse(DefaultPrefix)
  @JsonIgnore private[this] val dogstatsDSampleRate = sampleRate.getOrElse(DefaultSampleRate)
  @JsonIgnore private[this] val dogstatsDDebugMode : Boolean = debug.getOrElse(DefaultDebugMode)

  @JsonIgnore
  def mk(params: Stack.Params): DogstatsDTelemeter = {
    // initiate a UDP connection at startup time
    log.info("connecting to DogstatsD at %s:%d as %s", dogstatsDHost, dogstatsDPort, dogstatsDPrefix)
    val dogstatsDClient = new NonBlockingStatsDClient(
      dogstatsDPrefix,
      dogstatsDHost,
      dogstatsDPort,
      MaxQueueSize,
      dogstatsDConstantTags: _*
    )

    new DogstatsDTelemeter(
      new DogstatsDStatsReceiver(dogstatsDClient, dogstatsDSampleRate, dogstatsDDebugMode),
      dogstatsDInterval,
      DefaultTimer
    )
  }
}
