package app.domain.purchase

import cats.data.ValidatedNec
import zio.stream.Stream

import java.nio.file.Path

trait PurchaseRepository {
  def findAll(path: Path): Stream[Throwable, ValidatedNec[String, Purchase]]
}
