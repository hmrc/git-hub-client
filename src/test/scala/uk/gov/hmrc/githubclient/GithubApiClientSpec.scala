/*
 * Copyright 2021 HM Revenue & Customs
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

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.{Base64, Date}

import org.eclipse.egit.github.core._
import org.eclipse.egit.github.core.client.RequestException
import org.eclipse.egit.github.core.service.{OrganizationService}
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentCaptor, ArgumentMatchersSugar, Mockito, MockitoSugar}
import org.scalatest.{BeforeAndAfterEach, OneInstancePerTest}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}

class GithubApiClientSpec
  extends AnyWordSpecLike
     with Matchers
     with MockitoSugar
     with ArgumentMatchersSugar
     with ScalaFutures
     with BeforeAndAfterEach
     with OneInstancePerTest
     with IntegrationPatience {

  val rateLimitException = new RuntimeException("API rate limit exceeded for mr-robot")

  val apiRateLimitExceeded = APIRateLimitExceededException(rateLimitException)

  trait Setup {
    val mockOrgService: OrganizationService              = mock[OrganizationService]
    val mockTeamService: ExtendedTeamService             = mock[ExtendedTeamService]
    val mockRepositoryService: ExtendedRepositoryService = mock[ExtendedRepositoryService]
    val mockContentsService: ExtendedContentsService     = mock[ExtendedContentsService]
    val mockReleaseService: ReleaseService               = mock[ReleaseService]

    def githubApiClient = new GithubApiClient {
      val orgService: OrganizationService              = mockOrgService
      val teamService: ExtendedTeamService             = mockTeamService
      val repositoryService: ExtendedRepositoryService = mockRepositoryService
      val contentsService: ExtendedContentsService     = mockContentsService
      val releaseService                               = mockReleaseService
    }
  }

  import scala.collection.JavaConverters._

  "GitHubAPIClient.getOrganisations" should {
    "get all organizations" in new Setup {
      val users: java.util.List[User] =
        List(new User().setLogin("ORG1").setId(1), new User().setLogin("ORG2").setId(2)).asJava

      when(mockOrgService.getOrganizations)
        .thenReturn(users)

      githubApiClient.getOrganisations.futureValue shouldBe List(GhOrganisation("ORG1", 1), GhOrganisation("ORG2", 2))
    }

    "handle API rate limit error" in new Setup {
      when(mockOrgService.getOrganizations)
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.getOrganisations.failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.getTags" should {
    "returns list of tags" in new Setup {
      val tags: java.util.List[RepositoryTag] =
        List(new RepositoryTag().setName("tag1"), new RepositoryTag().setName("tag2")).asJava

      when(mockRepositoryService.getTags(any[IRepositoryIdProvider]))
        .thenReturn(tags)

      val captor = ArgumentCaptor.forClass(classOf[IRepositoryIdProvider])

      val resultTags: Future[List[String]] = githubApiClient.getTags("orgA", "repoA")

      resultTags.futureValue shouldBe List("tag1", "tag2")

      verify(mockRepositoryService).getTags(captor.capture())

      captor.getValue.generateId() shouldBe "orgA/repoA"
    }

    "handle API rate limit error" in new Setup {
      when(mockRepositoryService.getTags(any[IRepositoryIdProvider]))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.getTags("orgA", "repoA").failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.getTeamsForOrganisation" should {
    "get all team for organization" in new Setup {
      val teams: java.util.List[Team] =
        List(new Team().setName("Ateam").setId(1)).asJava

      when(mockTeamService.getTeams("ORG1"))
        .thenReturn(teams)

      githubApiClient.getTeamsForOrganisation("ORG1").futureValue shouldBe List(GhTeam("Ateam", 1))
    }

    "handle API rate limit error" in new Setup {
      when(mockTeamService.getTeams(any[String]))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.getTeamsForOrganisation("some-team").failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.getReposForTeam" should {
    "get all all repos for a team including archived status" in new Setup {
      private val nowDate = new Date()
      private val now     = nowDate.getTime

      private val fiveDaysAgo     = LocalDate.now().minusDays(5).toEpochDay
      private val fiveDaysAgoDate = new Date(fiveDaysAgo)

      val repos = List(ExtendedRepository(
        name        = "repoA",
        description = "some desc",
        id          = 1,
        htmlUrl     = "http://some/html/url",
        fork        = true,
        createdAt   = fiveDaysAgoDate,
        pushedAt    = nowDate,
        `private`   = true,
        language    = "Scala",
        archived    = true
      ))

      when(mockTeamService.getExtendedRepositories(1))
        .thenReturn(repos)

      githubApiClient.getReposForTeam(1).futureValue shouldBe List(
        GhRepository("repoA", "some desc", 1, "http://some/html/url", true, fiveDaysAgo, now, true, "Scala", true))
    }

    "default description and language to empty string" in new Setup {
      private val nowDate = new Date()
      private val now     = nowDate.getTime

      private val fiveDaysAgo     = LocalDate.now().minusDays(5).toEpochDay
      private val fiveDaysAgoDate = new Date(fiveDaysAgo)

      val repos = List(ExtendedRepository(
        name        = "repoA",
        description = null,
        id          = 1,
        htmlUrl     = "http://some/html/url",
        fork        = true,
        createdAt   = fiveDaysAgoDate,
        pushedAt    = nowDate,
        `private`   = false,
        language    = null,
        archived    = false
      ))

      when(mockTeamService.getExtendedRepositories(1))
        .thenReturn(repos)

      githubApiClient.getReposForTeam(1).futureValue shouldBe List(
        GhRepository("repoA", "", 1, "http://some/html/url", true, fiveDaysAgo, now, false, "", false))
    }

    "handle API rate limit error" in new Setup {
      when(mockTeamService.getExtendedRepositories(1))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.getReposForTeam(1).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  val captor = ArgumentCaptor.forClass(classOf[IRepositoryIdProvider])

  "GitHubAPIClient.repoContainsContent" should {
    "return true if it contains content at the given path" in new Setup {
      val path = "folder"

      val contents: java.util.List[RepositoryContents] = List(
        new RepositoryContents().setPath("folder"),
        new RepositoryContents().setPath("someOtherfolder")
      ).asJava

      when(mockContentsService.getContents(any[IRepositoryIdProvider], eqTo(path)))
        .thenReturn(contents)

      githubApiClient.repoContainsContent(path, "repoA", "OrgA").futureValue shouldBe true

      verify(mockContentsService).getContents(captor.capture(), eqTo(path))

      captor.getValue.generateId() shouldBe "OrgA/repoA"
    }

    "return false if it does not contain content at the given path" in new Setup {
      val path = "folder"

      when(mockContentsService.getContents(any[IRepositoryIdProvider], eqTo(path)))
        .thenReturn(List().asJava)

      githubApiClient.repoContainsContent(path, "repoA", "OrgA").futureValue shouldBe false
    }

    "re throw API rate limit exception" in new Setup {
      when(mockContentsService.getContents(any, any))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.repoContainsContent("some-path", "repoA", "OrgA").failed) { e =>
        e shouldBe APIRateLimitExceededException(rateLimitException)
      }
    }
  }

  "GitHubAPIClient.getFileContent" should {
    "return content decoded from base64 with line breaks if the file exists" in new Setup {
      val path    = s"folder/file.txt"
      val content = "Some contents"

      val contents: java.util.List[RepositoryContents] = List(
        new RepositoryContents()
          .setPath(path)
          .setName("file.txt")
          .setContent(Base64.getEncoder.encodeToString(content.getBytes(StandardCharsets.UTF_8)) + "\n")
      ).asJava

      when(mockContentsService.getContents(any[IRepositoryIdProvider], eqTo(path)))
        .thenReturn(contents)

      githubApiClient.getFileContent(path, "repoA", "OrgA").futureValue shouldBe Some(content)

      verify(mockContentsService).getContents(captor.capture(), eqTo(path))
      captor.getValue.generateId() shouldBe "OrgA/repoA"
    }

    "return None if the file does not exist" in new Setup {
      val path                                         = s"folder/file.txt"
      val contents: java.util.List[RepositoryContents] = List().asJava

      when(mockContentsService.getContents(any[IRepositoryIdProvider], eqTo(path)))
        .thenReturn(contents)

      githubApiClient.getFileContent(path, "repoA", "OrgA").futureValue shouldBe None

      verify(mockContentsService).getContents(captor.capture(), eqTo(path))
      captor.getValue.generateId() shouldBe "OrgA/repoA"
    }

    "return None if the path provided points to a directory" in new Setup {
      val path = s"folder/subfolder"
      val contents: java.util.List[RepositoryContents] = List(
        new RepositoryContents().setPath("folder/subfolder/file1.txt")
      ).asJava

      when(mockContentsService.getContents(any[IRepositoryIdProvider], eqTo(path)))
        .thenReturn(contents)

      githubApiClient.getFileContent(path, "repoA", "OrgA").futureValue shouldBe None

      verify(mockContentsService).getContents(captor.capture(), eqTo(path))
      captor.getValue.generateId() shouldBe "OrgA/repoA"
    }

    "re throw API rate limit exception" in new Setup {
      when(mockContentsService.getContents(any, any)).thenThrow(rateLimitException)

      whenReady(githubApiClient.getFileContent("some-path", "repoA", "OrgA").failed) { e =>
        e shouldBe APIRateLimitExceededException(rateLimitException)
      }
    }
  }

  "GitHubAPIClient.containsRepo" should {
    val owner    = "HMRC"
    val repoName = "test-repo"

    "return true when the repo exists" in new Setup {
      val repository = new Repository().setName(repoName)

      when(mockRepositoryService.getRepository(owner, repoName))
        .thenReturn(repository)

      githubApiClient.containsRepo(owner, repoName).futureValue shouldBe true
    }

    "return false when the repo does not exist" in new Setup {
      when(mockRepositoryService.getRepository(owner, repoName))
        .thenReturn(null)

      githubApiClient.containsRepo(owner, repoName).futureValue shouldBe false
    }

    "throw exception when egit encounters an error" in new Setup {
      when(mockRepositoryService.getRepository(owner, repoName))
        .thenThrow(new RequestException(new RequestError(), 500))

      intercept[Exception] {
        githubApiClient.containsRepo(owner, repoName).futureValue
      }
    }

    "re throw API rate limit exception" in new Setup {
      when(mockRepositoryService.getRepository(any, any))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.containsRepo("repoA", "OrgA").failed) { e =>
        e shouldBe APIRateLimitExceededException(rateLimitException)
      }
    }
  }

  "GitHubAPIClient.teamId" should {
    val organisation = "HMRC"
    val teamName     = "test-team"

    "find a team ID for a team name when the team exists" in new Setup {
      val team        = new Team().setId(123).setName(teamName)
      val anotherTeam = new Team().setId(321).setName("another-team")

      when(mockTeamService.getTeams(organisation))
        .thenReturn(List(team, anotherTeam).asJava)

      githubApiClient.teamId(organisation, teamName).futureValue.get shouldBe 123
    }

    "return None when the team does not exist" in new Setup {
      val anotherTeam = new Team().setId(321).setName("another-team")

      when(mockTeamService.getTeams(organisation))
        .thenReturn(List(anotherTeam).asJava)

      githubApiClient.teamId(organisation, teamName).futureValue shouldBe None
    }

    "handle API rate limit error" in new Setup {
      when(mockTeamService.getTeams(organisation))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.teamId(organisation, teamName).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.createRepo" should {
    val organisation = "HMRC"
    val repoName     = "test-repo"
    val cloneUrl     = s"git@github.com:hmrc/$repoName.git"

    "return the clone url for a successfully created repo" in new Setup {
      val repository = new Repository().setName(repoName).setCloneUrl(cloneUrl)

      when(mockRepositoryService.createRepository(eqTo(organisation), any[Repository]))
        .thenReturn(repository)

      val createdUrl = githubApiClient.createRepo(organisation, repoName).futureValue
      createdUrl shouldBe cloneUrl
    }

    "handle API rate limit error" in new Setup {
      when(mockRepositoryService.createRepository(eqTo(organisation), any[Repository]))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.createRepo(organisation, repoName).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.addRepoToTeam" should {
    val organisation = "HMRC"
    val repoName     = "test-repo"

    "add a repository to a team in" in new Setup {
      case class Arguments(teamId: Int, idProvider: IRepositoryIdProvider)

      val (future, answer) = buildAnswer2(Arguments.apply _)

      Mockito.when(mockTeamService.addRepository(any, any)).thenAnswer(answer)

      githubApiClient.addRepoToTeam(organisation, repoName, 99)

      val args = future.futureValue
      args.teamId                  shouldBe 99
      args.idProvider.generateId() shouldBe "HMRC/test-repo"
    }

    "handle API rate limit error" in new Setup {
      when(mockTeamService.addRepository(any, any))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.addRepoToTeam(organisation, repoName, 99).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.createFile" should {
    val organisation = "HMRC"
    val repoName     = "test-repo"
    val filePath     = "conf/app.config"
    val contents     = "some file contents"
    val message      = "created a file"

    "commit the file to the repository, base 64 encoding the contents" in new Setup {
      githubApiClient.createFile(organisation, repoName, filePath, contents, message).futureValue
      verify(mockContentsService)
        .createFile(organisation, repoName, filePath, "c29tZSBmaWxlIGNvbnRlbnRz", message)
    }

    "fail if commit message is empty" in new Setup {
      a[RuntimeException] should be thrownBy {
        githubApiClient.createFile(organisation, repoName, filePath, contents, "").futureValue
      }
    }

    "handle API rate limit error" in new Setup {
      when(mockContentsService.createFile(organisation, repoName, filePath, "c29tZSBmaWxlIGNvbnRlbnRz", message))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.createFile(organisation, repoName, filePath, contents, message).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  "GitHubAPIClient.getReposForOrg" should {
    val organisation = "HMRC"

    "get all repos for org" in new Setup {
      private val nowDate = new Date()
      private val now     = nowDate.getTime

      private val fiveDaysAgo     = LocalDate.now().minusDays(5).toEpochDay
      private val fiveDaysAgoDate = new Date(fiveDaysAgo)

      val repos = List(ExtendedRepository(
        name        = "repoA",
        description = "some desc",
        id          = 1,
        htmlUrl     = "http://some/html/url",
        fork        = true,
        createdAt   = fiveDaysAgoDate,
        pushedAt    = nowDate,
        `private`   = true,
        language    = "Scala",
        archived    = true
      ))

      when(mockRepositoryService.getOrgExtendedRepositories(organisation))
        .thenReturn(repos)

      githubApiClient.getReposForOrg(organisation).futureValue shouldBe List(
        GhRepository("repoA", "some desc", 1, "http://some/html/url", true, fiveDaysAgo, now, true, "Scala", true))
    }

    "return empty list when no repos exists" in new Setup {
      when(mockRepositoryService.getOrgExtendedRepositories(organisation))
        .thenReturn(List())

      githubApiClient.getReposForOrg(organisation).futureValue shouldBe List()
    }

    "handle API rate limit error" in new Setup {
      when(mockTeamService.getExtendedRepositories(1))
        .thenThrow(rateLimitException)

      whenReady(githubApiClient.getReposForTeam(1).failed) { e =>
        e shouldBe apiRateLimitExceeded
      }
    }
  }

  // NB -> This is a workaround for a bug in Mockito whereby a test file can't contain more than one captor of the same type
  def buildAnswer2[A, B, R](function: (A, B) => R) = {
    val promise = Promise[R]()
    val answer = new Answer[Unit] {
      override def answer(invocationOnMock: InvocationOnMock): Unit = {
        val rawArgs = invocationOnMock.getArguments

        val curried = convert(rawArgs) {
          function.curried
        }
        val arguments = convert(rawArgs.tail) {
          curried
        }

        promise.success(arguments)
      }

      def convert[I, O](args: Seq[AnyRef])(fn: I => O): O =
        fn(args.head.asInstanceOf[I])
    }

    (promise.future, answer)
  }
}
