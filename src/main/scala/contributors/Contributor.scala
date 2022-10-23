package contributors

import zio.stream.{ZPipeline, ZStream}

final case class Repository(name: String, contributors: List[Contributor])

final case class Contributor(login: String, contributions: Int)

final case class ContributorFileEntry(repositoryName: String, contributor: Contributor)


object ContributorService {
  def repositories(fileName: String): ZStream[Any, Throwable, Repository] =
    contributorFileEntries(fileName)
      .groupByKey(_.repositoryName) { case (repositoryName, stream) =>
        ZStream.fromZIO(stream.map(_.contributor).runCollect).map((repositoryName, _))
      }
      .map { case (repo, contributors) => Repository(repo, contributors.toList) }

  private def contributorFileEntries(fileName: String): ZStream[Any, Throwable, ContributorFileEntry] =
    ZStream.fromFileName(fileName)
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitLines)
      .map(_.split(","))
      .map {
        case Array(repositoryName, contributorLogin, contributions) =>
          ContributorFileEntry(repositoryName, Contributor(contributorLogin, contributions.toInt))
      }
}