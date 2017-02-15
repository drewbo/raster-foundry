package com.azavea.rf


import net.spy.memcached.internal.GetFuture

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{Executors, TimeUnit}

package object common {

  trait isFuture[A] {
    def asFuture[B]: Future[B]
  }

  implicit class memcachedFutureIsFuture[A <: AnyRef](fut: GetFuture[A]) extends isFuture[GetFuture[A]] {
    def asFuture[B]: Future[B] = {
      val promise = Promise[Object]()
      new Thread(new Runnable {
        def run() {
          promise.complete(Try{ fut.get })
        }
      }).start
      promise.future.map({ obj => obj.asInstanceOf[B] })
    }
  }

}
