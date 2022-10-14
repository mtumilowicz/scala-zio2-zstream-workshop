package app.infrastructure.purchase

import app.domain.purchase.{Purchase, PurchaseRepository}
import app.gateway.in.PurchaseApiInput
import cats.data.ValidatedNec
import zio.stream.{Stream, ZPipeline, ZStream}

import java.nio.file.Path


class PurchaseCsvFileRepository extends PurchaseRepository {
  override def findAll(path: Path): Stream[Throwable, ValidatedNec[String, Purchase]] =
    ZStream.fromFile(path.toFile)
      .via(ZPipeline.utf8Decode)
      .via(ZPipeline.splitLines)
      .drop(1)
      .map(PurchaseApiInput)
      .map(_.toDomain)
}