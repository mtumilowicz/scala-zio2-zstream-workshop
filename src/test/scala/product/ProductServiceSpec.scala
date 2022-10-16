package product

import zio.test.Assertion._
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertZIO}
import zio.{Chunk, Scope}

object ProductServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ProductService suite")(
      test("parse Products char by char") {
        val expectedProducts = Chunk(Product.Computer,Product.WashingMachine,Product.TV,Product.TV)
        val effect = ProductService.products.runCollect
        assertZIO(effect)(equalTo(expectedProducts))
      }
    )
}
