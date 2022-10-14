package app.domain.rating

import app.domain.purchase.PurchaseService
import cats.data.{NonEmptyChain, Validated}
import zio.stream.Stream

import java.nio.file.Path

case class RatingService(purchaseService: PurchaseService) {

  def findAll(path: Path): Stream[Throwable, Validated[NonEmptyChain[String], ProductRating]] =
    purchaseService.findAll(path)
      .map(_.map(ProductRating.from))

}
