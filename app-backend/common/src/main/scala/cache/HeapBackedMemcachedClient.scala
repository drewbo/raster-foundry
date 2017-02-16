package com.azavea.rf.common.cache

import com.github.blemale.scaffeine.{ Cache => ScaffeineCache, Scaffeine }
import net.spy.memcached._

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.matching._


class HeapBackedMemcachedClient[CachedType](
  client: MemcachedClient,
  options: HeapBackedMemcachedClient.Options = HeapBackedMemcachedClient.Options()) {

  /** This key sanitizer replaces whitespace with '_' and throws in case of control characters */
  def sanitizeKey(key: String): String = {
    // Control characters in the unicode spec
    val blacklist = "[^\u0020-\u007e^\u0009-\u000B]".r
    assert(key.length <= 250, s"Keys of length 250 or greater are not allowed; key provided has length of ${key.length}")
    assert(blacklist.findFirstIn(key) match {
      case Some(char) => false
      case None => true
    } , s"Invalid use of control character ( ${blacklist.findFirstIn(key).get} ) detected in key")
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
}

