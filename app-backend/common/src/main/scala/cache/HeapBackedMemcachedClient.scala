package com.azavea.rf.common.cache

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching._


class HeapBackedMemcachedClient[CachedType](
  client: MemcachedClient,
  options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

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

  val onHeapCache: ScaffeineCache[String, Future[CachedType]] =
    Scaffeine()
      .recordStats()
      .expireAfterAccess(options.ttl)
      .maximumSize(options.maxSize)
      .build[String, Future[CachedType]]()

  def caching(cacheKey: String)(expensiveOperation: String => Future[CachedType])(implicit ec: ExecutionContext): Future[CachedType] = {
    val sanitizedKey = sanitizeKey(cacheKey)
    val futureCached: Future[CachedType] =
      onHeapCache.get(sanitizedKey, { cKey: String => client.getOrSet[CachedType](cKey, expensiveOperation, options.ttl) })
    onHeapCache.put(sanitizedKey, futureCached)
    futureCached
  }
}

object HeapBackedMemcachedClient {
  case class Options(ttl: FiniteDuration = 2.seconds, maxSize: Int = 500)

  def apply[CachedType](client: MemcachedClient, options: Options = Options()) =
    new HeapBackedMemcachedClient[CachedType](client, options)

  // Example usage
  implicit val ec: ExecutionContext = ???
  val cli: MemcachedClient = ???
  val hbmc = HeapBackedMemcachedClient[Int](cli)
  def testing(str: String): Future[Int] =
    hbmc.caching(str) { str =>
      // Expensive, potentially blocking operations go here
      Future { str.toInt }
    }
}

