[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# scala-zio2-zstream-workshop

* references
    * https://www.zionomicon.com
    * https://zio.dev/version-1.x/datatypes/stream/zstream/
    * [Zymposium - ZIO Streams Part 3 (Streaming Sandwiches)](https://www.youtube.com/watch?v=SGr7mQ15-Pw)
    * [ZIO Stream — Part 2 — Sinks!](https://www.youtube.com/watch?v=T5vBs6_W_Xg)
    * https://blog.rockthejvm.com/zio-streams/
    * [ZIO-streams tutorial - build a Bitcoin ticker in 10 minutes](https://www.youtube.com/watch?v=sXYceYCLUZw)

## preface
* goals of this workshop
    * introduction to zio streams
        * Stream
        * Sink
        * Pipeline
* workshop task
    * task1: migration from fs2 to ZStream
        * base repo: https://github.com/mtumilowicz/scala-zio2-fs2-refined-newtype-workshop
            * replace fs2 with ZStream
    * task2: get BTC-EUR price every 5 seconds (answer: BitcoinTicker object)
        ```
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
              // constantly call getPrice, hint: repeatZIO
              // every 5 seconds, hint: throttleShape
              // println price, hint: foreach, s"BTC-EUR: ${price.setScale(2)}"
        }
        ```

## zstream
* components
    * `ZStream[R, E, O]`
        * effectual stream
        * requires an environment R
        * may fail with an error E
            * if a stream fails with an error it is not well defined to pull from that stream again
        * succeed with zero or more values of type O
        * pull-based
            * elements are processed by being "pulled through the stream" by the sink
        * ZIO vs ZStream
            * ZIO - single value
        * we can rewrite any chained ZStream as ZPipelines + ZSink
            * example
                ```
                stream >>> pipeline1 >>> pipeline2 >>> sink
                ```
            * motivation
                * very complex cases: good to have transformation/consumption as data types
    * `trait ZSink[-Env, +Err, -In, +Leftover, +Summary]`
        * describe ways of consuming elements
        * how to create?
            ```
            ZSink.fromFileName("README2.md")
            ```
        * example
            ```
            def run[R1 <: R, E1 >: E, B](sink: ZSink[R1, E1, A, Any, B]): ZIO[R1, E1, B]

            .run(ZSink.collectAll)
            ```
        * combining sinks
            ```
            outputSink1.zipPar(outputSink2) // send inputs to both
            ```
        * mental model
            ```
            trait ZSink[-Env, +Err, -In, +Leftover, +Summary] {
                def push: ZIO[Env with Scope, Nothing, Option[In] => ZIO[
                    Any,
                    (Either[Err, Summary], Leftover),
                    Unit]]
            }
            ```
            * Unit = need more input
            * fail with (Summary, Leftover) = done
                * Leftover = some sinks may not consume all of their inputs
                    * we cannot discard them as some other sink may want them
            * Option[In] = state of stream the sink is consuming from
                * Some = producing
                * None = done
    * `trait ZPipeline[-Env, +Err, -In, +Out]`
        * represents the "middle" of the stream
        * conceptually: stream transformation function
        * most useful applications of pipelines is for encoders and decoders
        * how to create?
            ```
            object ZPipeline {
                def map[In, Out](f: In => Out)(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out]
            }
            ```
        * example of usage
            ```
            def via[R1 <: R, E1 >: E, B](pipeline: ZPipeline[R1, E1, A, B]): ZStream[R1, E1, B]

            .via(ZPipeline.utf8Decode)
            ```
        * contramap
            * useful when we have a fixed output, and our existing function cannot consume those outputs
            * motivation
                * we have some logic to process a stream already
                * we want to apply logic to stream of different type
            * category theory
                * Covariant Functor: map
                    * produce value A
                    * example: covariant Decoder[A]
                        * JSON => A
                * Contravariant Functor: contramap
                    * consumes value A
                    * example: JSON contravariant Encoder[A]
                        * A => JSON
            * example
                ```
                  val numericSum: ZSink[Any, Nothing, Int, Nothing, Int]    =
                    ZSink.sum[Int]

                  val stringSum : ZSink[Any, Nothing, String, Nothing, Int] =
                    numericSum.contramap((x: String) => x.toInt) // done on the sink side (contramap)

                  val sum: ZIO[Any, Nothing, Int] =
                    ZStream("1", "2", "3", "4", "5").run(stringSum)

                  val sum: ZIO[Any, Nothing, Int] =
                    ZStream("1", "2", "3", "4", "5").map(_.toInt).run(numericSum) // done on the stream side (map)
                ```
    * trait `ZChannel[-Env, -InErr, -InElem, -InDone, +OutErr, +OutElem, +OutDone]`
        * unifies streams, sinks, and pipelines
        * `type ZStream[-R, +E, +A] = ZChannel[R, Any, Any, Any, E, Chunk[A], Any]`
            * Any = does not need / does not produce
        * `type ZSink[-R, +E, -In, +L, +Z] = ZChannel[R, Nothing, Chunk[In], Any, E, L, Z]`
            * sink itself doesn’t know how to handle any errors so the error type has to be Nothing
        * `type ZPipeline[-R, +E, -In, +Out] = ZChannel[R, Nothing, Chunk[In], Any, E, Chunk[Out], Any]`
        * `type ZIO[-R, +E, +A] = ZChannel[R, Any, Any, Any, E, Nothing, A]`
* under the hood
    * implicit chunking
        ```
        trait ZStream[-R, +E, +O] {
            def process: ZIO[R with Scope, Option[E], Chunk[O]] // motivation: efficiency
        }
        ```
        however, filter and map work on individual values
* useful operators
    * collect = map + filter
    * concat - switch to other stream after this stream is done
    * mapAccum - map with stateful function
        ```
        def mapAccum[S, A1](s: => S)(f: (S, A) => (S, A1))
        ```
    * unfold
        * declaration
            ```
            def unfold[S, A](s: S)(f: S => Option[(A, S)]): ZStream[Any, Nothing, A]
            ```
        * is only evaluated as values are pulled
            * can be used to describe streams that continue forever
        * effectual variant: unfoldZIO
            * example: reading incrementally from a data source while maintaining some cursor
    * many operators have effectual variants (ZIO suffix)
        * for effectual variants - many have parallel variants (Par suffix)
        * example: map, mapZIO, mapZIOPar
* running stream
    1. transform ZStream to a ZIO effect
        * ZStream produces potentially infinitely many values
            * how to run a stream to produce a single value (ZIO effect)?
                * run stream and discard results (runDrain)
                * return the first value (runHead)
                * fold to produce summary, consume only as many elements as necessary to produce summary
            * example
                * get tweets -> transform -> save to db
                * entire program described as a stream
                    * no need for any result, just run it
    1. execute ZIO effect
* scope
    * ZIO workflow never produces any incremental output
        * it is clear that the finalizer should be run immediately after ZIO completes execution
    * general rule: finalizer should be run immediately after stream completes execution
        * we don’t want to run finalizers associated with an upstream channel while a downstream channel
        is still processing elements
    * example
        ```
          val finalizer: URIO[Any, Unit] = Console.printLine("finalizer finished").orDie
          def logging(prefix: String): Any => URIO[Any, Unit] = v => Console.printLine(s"$prefix " + v).orDie
          val businessLogic: Int => UStream[Int] = (v: Int) =>
            ZStream.fromZIO(Console.printLine(s"flatMap $v").orDie) *> ZStream.succeed(v)
          val stream1 = ZStream(1, 2, 3, 4)
          val stream2 = ZStream(5, 6, 7, 8)

          val ex1 = stream1
            .ensuring(finalizer)
            .tap(logging("first tapping"))
            .flatMap(businessLogic)
            .concat(stream2)
            .tap(logging("second tapping"))
            .runDrain
        ```
        results:
        ```
        first tapping 1
        flatMap 1
        second tapping 1
        ...
        second tapping 4
        finalizer finished
        second tapping 5
        ...
        second tapping 8
        ```
