package com.azavea.rf.tile

import java.util.UUID

import geotrellis.raster._
import geotrellis.raster.histogram.Histogram
import geotrellis.spark._
import geotrellis.raster.io._
import geotrellis.spark.io._
import geotrellis.spark.io.s3.{S3AttributeStore, S3ValueReader}
import scala.concurrent.Future

import com.github.blemale.scaffeine.{ AsyncLoadingCache, Scaffeine }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import spray.json.DefaultJsonProtocol._

/**
  * ValueReaders need to read layer metadata in order to know how to decode (x/y) queries into resource reads.
  * In this case it requires reading JSON files from S3, which are cached in the reader.
  * Naturally we want to cache this access to prevent every tile request from re-fetching layer metadata.
  * Same logic applies to other layer attributes like layer Histogram.
  */
object LayerCache extends Config {
  val cacheReaders: AsyncLoadingCache[(String, String, UUID, Int), Reader[SpatialKey, MultibandTile]] =
    Scaffeine()
    .recordStats()
    .expireAfterWrite(cacheExpiration)
    .maximumSize(cacheSize)
    .buildAsyncFuture { case (bucket: String, prefix: String, id: UUID, zoom: Int) =>
      val layerId = LayerId(id.toString, zoom)
      Future { S3ValueReader(bucket, prefix).reader[SpatialKey, MultibandTile](layerId) }
    }

  /**
    * Fetch cached tile reader
    *
    * @param bucket S3 bucket
    * @param prefix Key Prefix inside the S3 bucket
    * @param layerId LayerId in catalog stored in prefix
    * @param zoom    Pyramid zoom level
    */
  def tileReader(bucket: String, prefix: String, layerId: UUID, zoom: Int): Future[Reader[SpatialKey, MultibandTile]] =
    cacheReaders.get((bucket, prefix, layerId, zoom))

  def tile(bucket: String, prefix: String, layerId: UUID, zoom: Int, key: SpatialKey): Future[MultibandTile] =
    for ( reader <- tileReader(bucket, prefix, layerId, zoom))
      yield reader.read(key)

  def tile(prefix: String, layerId: UUID, zoom: Int, key: SpatialKey): Future[MultibandTile] =
    tile(defaultBucket, prefix, layerId, zoom, key)

  val cacheHistogram: AsyncLoadingCache[(String, String, UUID, Int), Array[Histogram[Double]]] =
    Scaffeine()
      .recordStats()
      .expireAfterWrite(cacheExpiration)
      .maximumSize(cacheSize)
      .buildAsyncFuture { case (bucket: String, prefix: String, id: UUID, zoom: Int) =>
        val layerId = LayerId(id.toString, 0) // use the same histogram for all zoom levels
        Future { S3AttributeStore(bucket, prefix).read[Array[Histogram[Double]]](layerId, "histogram") }
      }

  /**
    * Fetch cached layer attribute, Histogram
    *
    * @param bucket S3 bucket
    * @param prefix Key Prefix inside the S3 bucket
    * @param layerId LayerId in catalog stored in prefix
    * @param zoom    Pyramid zoom level
    */
  def bandHistogram(bucket: String, prefix: String, layerId: UUID, zoom: Int): Future[Array[Histogram[Double]]] =
    cacheHistogram.get((bucket, prefix, layerId, zoom))

  def bandHistogram(prefix: String, layerId: UUID, zoom: Int): Future[Array[Histogram[Double]]] =
    bandHistogram(defaultBucket, prefix, layerId, zoom)
}