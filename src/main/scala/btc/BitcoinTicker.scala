package btc

import zio.stream.ZStream
import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault, durationInt}

import scala.io.Source

object BitcoinTicker extends ZIOAppDefault {

  val url = "https://api.kraken.com/0/public/Ticker?pair=BTCEUR"

  val getPrice = ZIO.fromAutoCloseable(ZIO.attempt(Source.fromURL(url)))
    .map(_.mkString)
    .map(extractPrice)
  
  def extractPrice(json: String): BigDecimal =
    BigDecimal.apply(
      """c":\["([0-9.]+)"""
        .r("priceGroup")
        .findAllIn(json)
        .group("priceGroup")
    )

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    ZStream
      .repeatZIO(getPrice)
      .throttleShape(1, 5.seconds)(_ => 1)
      .foreach(price => zio.Console.printLine(s"BTC-EUR: ${price.setScale(2)}"))
}
