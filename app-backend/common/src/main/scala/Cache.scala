package com.azavea.rf.common

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching._


class HeapBackedMemcachedClient[From](client: MemcachedClient, options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

  def sanitizeKey(key: String): String = {
    assert(key.length <= 250)
    assert {
      val blacklist = "[^\u0000-\u001f\u007f-\u009f]".r
      blacklist.findFirstIn(key) match {
        case Some(char) => false
        case None => true
      }
    }
    val spaces = "[ \n\t\r]".r
    spaces.replaceAllIn(key, "_")
  }

  val onHeapCache: ScaffeineCache[String, Future[From]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(options.ttl)
      .maximumSize(options.maxSize)
      .build[String, Future[From]]()

  def caching(cacheKey: String)(expensiveOperation: String => From)(implicit ec: ExecutionContext): Future[From] = {
    val sanitizedKey = sanitizeKey(cacheKey)
    val futureFrom: Future[From] =
      onHeapCache.get(sanitizedKey, { cKey: String => client.getOrSet[From](cKey, expensiveOperation, options.ttl) })
    onHeapCache.put(sanitizedKey, futureFrom)
    futureFrom
  }
}

object HeapBackedMemcachedClient {
  case class Options(ttl: FiniteDuration = 2.seconds, maxSize: Int = 500)

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

