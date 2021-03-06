package com.azavea.rf

import com.azavea.rf.datamodel._

package object image extends RfJsonProtocols {

  implicit val paginatedImagesFormat = jsonFormat6(PaginatedResponse[Image.WithRelated])

}
