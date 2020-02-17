package net.oda.gitlab

import java.time.ZonedDateTime

import net.oda.IT
import org.scalatest.{AsyncFreeSpec, Matchers}

class GitlabClientTest extends AsyncFreeSpec with Matchers {
  "should get namespaces" taggedAs IT in {
    GitlabClient.getNamespaces().map(r => r should not be empty)
  }

  "should get project" taggedAs IT in {
    GitlabClient.getProject("empirica-algo/libs/gitlab-client")
      .map(r => r.name should not be empty)
  }

  "should get commits" taggedAs IT in {
    GitlabClient.getProject("empirica-algo/libs/gitlab-client")
      .map(_.id)
      .flatMap(GitlabClient.getCommits(_, "develop", ZonedDateTime.now().minusYears(5)))
      .map(r => r should not be empty)
  }

  "should get commit" taggedAs IT in {
    GitlabClient.getProject("empirica-algo/libs/gitlab-client")
      .flatMap(
        p => GitlabClient.getCommits(p.id, "develop", ZonedDateTime.now().minusYears(5))
          .flatMap(cs => GitlabClient.getCommit(p.id, cs.head.short_id))
      )
      .map(r => r.title should not be empty)
  }


}
