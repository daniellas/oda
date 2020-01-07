package net.oda.gitlab

import net.oda.IT
import org.scalatest.{AsyncFreeSpec, Matchers}

import scala.concurrent.Future

class GitlabClientTest extends AsyncFreeSpec with Matchers {
  "should get namespaces" taggedAs IT in {
    GitlabClient.getNamespaces().map(r => r should not be empty)
  }

  "should get project" taggedAs IT in {
    GitlabClient.getProject("empirica-algo/libs/gitlab-client").map(r => r should not be empty)
  }

  "should get commits" taggedAs IT in {
    GitlabClient.getProject("empirica-algo/libs/gitlab-client")
      .flatMap(p => p.map(_.id).map(GitlabClient.getCommits(_, "develop")).getOrElse(Future.failed(new NoSuchElementException())))
      .map(r => r should not be empty)
  }

}
