package com.azavea.rf.common

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._

class HeapBackedMemcachedClient[From](client: MemcachedClient, options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

  private val onHeapCache: ScaffeineCache[String, Future[From]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(options.ttl)
      .maximumSize(options.maxSize)
      .build[String, Future[From]]()


  private def fetchRemote(cacheKey: String, expensiveGet: String => From)(implicit ec: ExecutionContext): Future[From] = {
    val futureFrom = Future { client.asyncGet(cacheKey).get() }
    futureFrom.flatMap({ value =>
      if (value != null) { // cache hit
        Future { value.asInstanceOf[From] }
      } else { // cache miss
        val futureFrom: Future[From] = Future { expensiveGet(cacheKey) }
        futureFrom.foreach({ fromValue => client.set(cacheKey, options.ttl.toSeconds.toInt, fromValue) })
        futureFrom
      }
    })

  }

  def caching(cacheKey: String)(expensiveGet: String => From)(implicit ec: ExecutionContext): Future[From] = {

    val futureMaybeFrom: Future[From] =
      onHeapCache.get(cacheKey, { cKey: String => fetchRemote(cKey, expensiveGet) })

      val futureMaybeTile = onHeapCache.get(cacheKey, { cKey: String => fetchRemote(cacheKey, expensiveGet) })
      onHeapCache.put(cacheKey, futureMaybeTile)
      futureMaybeTile
  }
}

object HeapBackedMemcachedClient {
  case class Options(ttl: FiniteDuration = 30.seconds, maxSize: Int = 500)

  def apply[From](client: MemcachedClient, options: Options = Options()) =
    new HeapBackedMemcachedClient[From](client, options)

  // Example usage
  implicit val ec: ExecutionContext = ???
  val cli: MemcachedClient = ???
  val hbmc = HeapBackedMemcachedClient[Int](cli)
  def testing(str: String): Future[Int] =
    hbmc.caching(str) { str =>
      // Expensive, potentially blocking operations go here
      str.toInt
    }
}

