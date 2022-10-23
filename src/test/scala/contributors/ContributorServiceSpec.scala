package contributors

import zio.test.Assertion.equalTo
import zio.test._

object ContributorServiceSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("ProductService suite")(
      test("verify number of repos") {
        val expectedGroups = 3
        val groups = ContributorService.repositories("src/test/resources/contributors/data.txt").runCollect
        assertZIO(groups.map(_.size))(equalTo(expectedGroups))
      }
    )
}
