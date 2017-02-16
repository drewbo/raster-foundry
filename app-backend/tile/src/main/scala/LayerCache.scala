package com.azavea.rf.tile

import com.azavea.rf.database.Database
import com.azavea.rf.common.cache._

import com.github.benmanes.caffeine.cache.Caffeine

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader}
import com.github.blemale.scaffeine.{ Cache => ScaffCache, Scaffeine }
import scalacache.caffeine.CaffeineCache
import scalacache.memcached.MemcachedCache
import com.github.benmanes.caffeine.cache._
import com.github.benmanes.caffeine.cache.Caffeine
import scalacache.serialization.InMemoryRepr
import scalacache._
import spray.json.DefaultJsonProtocol._
import net.spy.memcached._
import java.net.InetSocketAddress

import scala.concurrent._
import scala.concurrent.duration._
import scala.util._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.{Executors, TimeUnit}

/**
  * ValueReaders need to read layer metadata in order to know how to decode (x/y) queries into resource reads.
  * In this case it requires reading JSON files from S3, which are cached in the reader.
  * Naturally we want to cache this access to prevent every tile request from re-fetching layer metadata.
  * Same logic applies to other layer attributes like layer Histogram.
  *
  * Things that are cheap to construct but contain internal state we want to re-use use LoadingCache.
  * things that require time to generate, usually a network fetch, use AsyncLoadingCache
  */
object LayerCache extends Config {
  implicit lazy val database = Database.DEFAULT
  val blockingExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(64))

  val memcachedClient =
    new MemcachedClient(new InetSocketAddress(memcachedHost, memcachedPort))


  // TODO: Make a scalacache Codec using Kryo
  implicit val memcached: ScalaCache[Array[Byte]] = {
    ScalaCache(MemcachedCache(memcachedClient))
  }

  implicit val memoryCache: ScalaCache[InMemoryRepr] = {
    val underlyingCaffeineCache =
      Caffeine.newBuilder()
        .maximumSize(cacheSize)
        .expireAfterAccess(cacheExpiration.toMillis, TimeUnit.MILLISECONDS)
        .build[String, Object]
    ScalaCache(CaffeineCache(underlyingCaffeineCache))
  }

  def attributeStore(bucket: String, prefix: Option[String]): Future[S3AttributeStore] =
    caching[S3AttributeStore, InMemoryRepr](s"store-$bucket-$prefix"){
      prefix match {
        case Some(prefixStr) => Future.successful(S3AttributeStore(bucket, prefixStr))
        case None => Future.failed(new LayerIOError("Scene has no ingest location"))
      }
    }

  def attributeStore(prefix: Option[String]): Future[S3AttributeStore] =
    attributeStore(defaultBucket, prefix)


  val tileCache = HeapBackedMemcachedClient[Option[MultibandTile]](memcachedClient)
  def maybeTile(id: RfLayerId, zoom: Int, key: SpatialKey): Future[Option[MultibandTile]] =
    tileCache.caching(s"tile-$id-$zoom-$key") { cacheKey =>
      for {
        prefix <- id.prefix
        store <- attributeStore(defaultBucket, prefix)
      } yield {
        val reader = new S3ValueReader(store).reader[SpatialKey, MultibandTile](id.catalogId(zoom))
        Try(reader.read(key)) match {
          // Only cache failures through failed query
          case Success(tile) => Some(tile)
          case Failure(e: ValueNotFoundError) => None
          case Failure(e) => throw e
        }
      }
    }


  val histogramCache = HeapBackedMemcachedClient[Array[Histogram[Double]]](memcachedClient)
  def bandHistogram(id: RfLayerId, zoom: Int): Future[Array[Histogram[Double]]] =
    histogramCache.caching(s"histogram-$id-$zoom") { cacheKey =>
      for {
        prefix <- id.prefix
        store <- attributeStore(defaultBucket, prefix)
      } yield store.read[Array[Histogram[Double]]](id.catalogId(0), "histogram")
    }
}
