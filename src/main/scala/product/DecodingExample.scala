package product

import zio.{Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

object DecodingExample extends ZIOAppDefault {

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    ProductService.products.tap(ProductService.buy).runCollect
}
