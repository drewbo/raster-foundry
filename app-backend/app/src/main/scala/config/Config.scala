package com.azavea.rf.config

import scala.concurrent.{Future, ExecutionContext}
import scala.collection.JavaConversions._
import com.azavea.rf.utils.Config
import com.azavea.rf.database.Database
import com.azavea.rf.AkkaSystem

case class FeatureFlag(key: String, active: Boolean, name: String, description: String)
case class AngularConfig(clientId: String, auth0Domain: String, featureFlags: Seq[FeatureFlag])

object AngularConfigService extends AkkaSystem.LoggerExecutor with Config {
  def getConfig():
      AngularConfig = {

    val features: Seq[FeatureFlag] = featureFlags.map { featureConfig =>
      FeatureFlag(
        featureConfig.getString("key"),
        featureConfig.getBoolean("active"),
        featureConfig.getString("name"),
        featureConfig.getString("description")
      )
    }.toSeq
    implicit val featureFlagFormat = jsonFormat4(FeatureFlag)

    return AngularConfig(auth0ClientId, auth0Domain, features)
  }
}
