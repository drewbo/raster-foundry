package com.azavea.rf.datasource

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.ParameterDirectives.parameters

import com.azavea.rf.database.query._
import com.azavea.rf.utils.queryparams._

trait DatasourceQueryParameterDirective extends QueryParametersCommon {
  val datasourceQueryParams = parameters((
    'name.as[String].?
  )).as(DatasourceQueryParameters)
}
