package app.gateway

import app.domain.analysis.ProductAnalysisService
import app.domain.rating.{ProductRating, RatingService}
import app.gateway.out.{ParsingSummary, ProductRatingAnalysisApiOutput}
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNec
import zio.{Task, UIO, ZIO}

import java.nio.file.Path

case class AnalysisService(analysisService: ProductAnalysisService,
                           ratingService: RatingService) {

  def calculate(path: Path): Task[ProductRatingAnalysisApiOutput] = for {
    parsingSummary <- ratingService.findAll(path)
      .mapZIO(addToStatistics)
      .runFold(ParsingSummary.zero()) {
        case (summary, Invalid(_)) => summary.invalidLineSpotted()
        case (summary, Valid(_)) => summary.validLineSpotted()
      }
    analysis <- analysisService.analyse()
  } yield ProductRatingAnalysisApiOutput.fromDomain(analysis, parsingSummary)

  private def addToStatistics(validatedPurchase: ValidatedNec[String, ProductRating]): UIO[ValidatedNec[String, ProductRating]] =
    validatedPurchase match {
      case Valid(a) =>
        analysisService.addToStatistics(a) *> ZIO.succeed(validatedPurchase)
      case Invalid(_) => ZIO.succeed(validatedPurchase)
    }
}
