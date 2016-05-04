/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.gitclient

import java.util.Date

import org.joda.time.DateTime
import play.api.libs.json.Json

case class GhOrganisation(login: String, id: Int = 0)

case class GhTeam(name: String, id: Long)

case class GhRepository(name: String, id: Long, html_url: String,  fork: Boolean = false)

case class GhRepoRelease(id : Long, tag_name:String, created_at : DateTime)


object GhRepoRelease {
  implicit val formats = Json.format[GhRepoRelease]
}



object GhTeam {
  implicit val formats = Json.format[GhTeam]
}

object GhOrganisation {
  implicit val formats = Json.format[GhOrganisation]
}

object GhRepository {
  implicit val formats = Json.format[GhRepository]
}
