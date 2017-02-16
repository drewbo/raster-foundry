package com.azavea.rf


import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{Executors, TimeUnit}

package object common {
  implicit class MemcachedClientMethods(client: MemcachedClient) {
    def getOrSet[CachedType](cacheKey: String, expensiveOperation: String => CachedType, ttl: Duration)(implicit ec: ExecutionContext): Future[CachedType] = {
      val futureCached = Future { client.asyncGet(cacheKey).get() }
      futureCached.flatMap({ value =>
        if (value != null) { // cache hit
          Future { value.asInstanceOf[CachedType] }
        } else { // cache miss
          val futureCached: Future[CachedType] = Future { expensiveOperation(cacheKey) }
          futureCached.foreach({ fromValue => client.set(cacheKey, ttl.toSeconds.toInt, fromValue) })
          futureCached
        }
      })
    }
  }
}
