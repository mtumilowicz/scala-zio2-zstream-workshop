package product

sealed trait Product

object Product {
  case object Computer extends Product

  case object TV extends Product

  case object WashingMachine extends Product

  val mapping: Map[String, Product] =
    Map(
      "Computer" -> Product.Computer,
      "TV" -> Product.TV,
      "WashingMachine" -> Product.WashingMachine
    )
}