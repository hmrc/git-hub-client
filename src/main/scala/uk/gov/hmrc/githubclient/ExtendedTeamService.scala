/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.githubclient

import java.util

import org.eclipse.egit.github.core.service.TeamService
import org.eclipse.egit.github.core.{IRepositoryIdProvider, Team}

class ExtendedTeamService(client: ExtendedGitHubClient) extends TeamService(client) {

  override def addRepository(id: Int, repository: IRepositoryIdProvider): Unit = {

    val repoId = this.getId(repository)
    val uri    = new StringBuilder("/teams")
    uri.append('/').append(id)
    uri.append("/repos")
    uri.append('/').append(repoId)

    val params = new util.HashMap[String, String]()
    params.put("permission", "push")

    val headers = Map("Accept" -> "application/vnd.github.ironman-preview+json")

    client.put(uri.toString(), params, headers, classOf[Team])
  }
}
