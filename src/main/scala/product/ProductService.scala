package product

import zio.stream.{UStream, ZChannel, ZPipeline, ZStream}
import zio.{Chunk, ChunkBuilder, UIO, ZIO, ZNothing}

object ProductService {

  def buy(product: Product): UIO[Unit] = ZIO.debug(s"Product ${product.toString} bought!")

  val encodedProducts: UStream[Char] = ZStream.fromChunk(
    Chunk.fromIterable("Computer" + "WashingMachine" + "TV" + "TV")
  )

  val decodeProduct: ZPipeline[Any, Nothing, Char, Product] = {
    // read some input
    // decode as many products as we can, maybe some leftovers appear
    // emit decoded products
    // read more input and add it to the leftovers
    // repeat
    def read(buffer: Chunk[Char]): ZChannel[Any, ZNothing, Chunk[Char], Any, Nothing, Chunk[Product], Any] = {
      ZChannel.readWith(
        (in: Chunk[Char]) => {
          val (leftovers, products) = processBuffer(buffer, in)
          ZChannel.writeAll(products) *> read(leftovers)
        },
        (err: ZNothing) => ZChannel.fail(err),
        (done: Any) => ZChannel.fromZIO(
          ZIO.debug(s"WARNING: PRODUCT FRAGMENT ${buffer.mkString("")}").when(buffer.nonEmpty))
          *> ZChannel.succeed(done)
      )
    }

    ZPipeline.fromChannel(read(Chunk.empty))
  }

  val products =
    encodedProducts >>> decodeProduct

  private def processBuffer(buffer: Chunk[Char], input: Chunk[Char]): (Chunk[Char], Chunk[Product]) = {
    val combined = buffer ++ input
    val iterator = combined.iterator
    val productBox = ChunkBuilder.make[Product]()
    var candidate = ""
    while (iterator.hasNext) {
      var char = iterator.next()
      candidate += char
      Product.mapping.get(candidate) match {
        case Some(value) =>
          productBox.addOne(value)
          candidate = ""
        case None =>
          ()
      }
    }
    (Chunk.fromIterable(candidate), productBox.result())
  }


}
