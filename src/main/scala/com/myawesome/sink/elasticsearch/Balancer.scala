package com.myawesome.sink.elasticsearch

import java.time.{Duration => JDuration}
import java.time.temporal.ChronoUnit
import scala.concurrent.duration.{Duration, DurationInt, DurationLong}

/**
 * Balancer of consumption and production timing
 *   FIXME: This is a stub, first attempt on forward and backward pressure.
 * @param await time that consumption will wait until Kafka consumer poll times out
 * @param pause time that consumption/producer will pause after every block
 * @param min minimum duration for await and pause
 * @param max maximum duration for await and pause
 * @param threshold threshold time to trigger the update function
 */
class Balancer(private var await: Duration,
               private var pause: Duration,
               min: Duration = 100.millis,
               max: Duration = 1000.millis,
               threshold: Duration = 200.millis
              ) {

  assert(max > min && threshold.toMillis > 0)
  private var push: Duration = min + (max - min)/2

  def timeout: JDuration = {
    JDuration.of(await.toMillis, ChronoUnit.MILLIS)
  }

  def block: Duration = await
  def idle: Duration = pause

  def update(newPush:Duration): Unit = {
    val diff = (newPush - push).toMillis
    if (diff.abs > threshold.toMillis) {
      val correction = clip(pause - threshold/5 * diff.sign)
      pause = correction
      await = correction
      push = newPush
    }
  }

  def time[T](block: => T): T = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    update((t1 - t0).nanos)
    result
  }

  private def clip(d: Duration): Duration = {
    if (d <= min) min
    else if (d >= max) max
    else d
  }

  def sleep(): Unit = {
    Thread.sleep(pause.toMillis)
  }
}

object Balancer {
  def apply(block: Duration = 200.millis, pause: Duration = 200.millis) =
    new Balancer(block, pause)
}
