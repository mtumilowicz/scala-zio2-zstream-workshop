package app.domain.purchase

import cats.data.ValidatedNec
import zio.stream.Stream

import java.nio.file.Path

case class PurchaseService(repository: PurchaseRepository) {
  def findAll(path: Path): Stream[Throwable, ValidatedNec[String, Purchase]] =
    repository.findAll(path)
}
