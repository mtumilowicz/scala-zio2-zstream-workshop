[![Build Status](https://app.travis-ci.com/mtumilowicz/scala-zio2-zstream-workshop.svg?branch=master)](https://app.travis-ci.com/mtumilowicz/scala-zio2-zstream-workshop)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# scala-zio2-zstream-workshop

* references
    * https://www.zionomicon.com
    * https://zio.dev/version-1.x/datatypes/stream/zstream/
    * [Zymposium - ZIO Streams Part 3 (Streaming Sandwiches)](https://www.youtube.com/watch?v=SGr7mQ15-Pw)
    * [ZIO Stream — Part 2 — Sinks!](https://www.youtube.com/watch?v=T5vBs6_W_Xg)
    * https://blog.rockthejvm.com/zio-streams/
    * [ZIO-streams tutorial - build a Bitcoin ticker in 10 minutes](https://www.youtube.com/watch?v=sXYceYCLUZw)
    * https://j3t.ch/tech/zio-streams-trappings/
    * https://github.com/adamgfraser/0-to-100-with-zio-test

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
    * task3: implement decoding `ZChannel`
        * input: concatenated products char by char
            * `ProductService.encodedProducts.runCollect.flatMap(zio.Console.printLine(_))`
            * `Chunk(C,o,m,p,u,t,e,r,W,a,s,h,i,n,g,M,a,c,h,i,n,e,T,V,T,V)`
        * output: parsed products
            * `ProductService.products.runCollect.flatMap(zio.Console.printLine(_))`
            * `Chunk(Computer,WashingMachine,TV,TV)`
        * method to implement
            ```
              val decodeProduct: ZPipeline[Any, Nothing, Char, Product] = {
                // read some input
                // decode as many products as we can, maybe some leftovers appear
                // emit decoded products
                // read more input and add it to the leftovers
                // repeat
                def read(buffer: Chunk[Char]): ZChannel[Any, ZNothing, Chunk[Char], Any, Nothing, Chunk[Product], Any] = {
                  // read from input, hint: ZChannel.readWith
                  // use process buffer, hint: (leftovers, products)
                  // write to channel, hint: ZChannel.writeAll
                  // repeat with leftovers, hint: read(leftovers)
                  // handle error channel, hint: ZNothing, ZChannel.fail
                  // handle done, hint: if buffer not empty - ZIO.debug error, otherwise ZChannel.succeed
                }

                ZPipeline.fromChannel(read(Chunk.empty))
              }
            ```
        * solution: `ProductService`
    * task5
        * read from `src/test/resources/contributors/data.txt` and group contributors by repository
            * solution: `ContributorService`
        * you could verify number of lines using:
            ```
            cat ~/IdeaProjects/scala-zio2-zstream-workshop/src/test/resources/contributors/data.txt | awk -F, '{ print $1 }' | sort -u | wc -l
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
        * vs ZIO: ZIO - single value
    * `trait ZSink[-Env, +Err, -In, +Leftover, +Summary]`
        * describe ways of consuming elements
        * composable aggregation strategy
        * strategy for aggregating zero or more elements into a summary value
        * sink may emit zero or more leftover values of type `Chunk[+Leftover]`
            * represents inputs that were received but not included in the aggregation
                * in some cases it can be useful to keep leftovers for further processing
            * chunking can lead to leftovers
                * sink does not need all of the elements in the chunk to produce a summary value
                * example
                    * suppose that we want to have 3 elements, but results are produced with 2 x two-element chunks
            * example
                ```
                ZSink.collectAllWhile(_ == "a")
                ```
                * suppose inputs: "a" then "b"
                    * "b" is leftover because we have to consume it (execute check `_ == "a"`) to decide if the sink is done
        * how to create?
            ```
            ZSink.fromFileName("README2.md")
            ```
        * example
            ```
            def run[R1 <: R, E1 >: E, B](sink: ZSink[R1, E1, A, Any, B]): ZIO[R1, E1, B]

            stream.run(ZSink.collectAll)
            ```
        * combining sinks
            ```
            outputSink1.zipPar(outputSink2) // send inputs to both
            ```
        * asynchronous aggregations
            * size of a chunk + duration (to not wait eternally for filling chunk)
            * example
                ```
                def aggregateAsyncWithin[R1 <: R, E1 >: E, A1 >: A, B](
                    sink: => ZSink[R1, E1, A1, A1, B],
                    schedule: => Schedule[R1, Option[B], Any]
                    )
                ```
                * if the sink is done first => aggregated value will be emitted downstream
                    * previous schedule timeout will be canceled
                    * then run the sink again and the next recurrence of the schedule
                * if the schedule is done first => write a done value to sink
                    * writes its aggregated value to the downstream immediately
                    * then run the sink again and the next recurrence of the schedule
    * `trait ZPipeline[-Env, +Err, -In, +Out]`
        * represents the "middle" of the stream
            * streams: beginning of a data flow process
            * sinks: end of a data flow process
        * takes as input a stream and returns a new stream of different element type
            * definition is extremely broad
                * almost any stream operator can be described as a pipeline
                * can: map, filter, aggregate, append, etc
                * can't: provide environment or handle stream errors
        * strategy for describing transformations, not error handling
        * use case: encoders and decoders
            * encoding or decoding should be completely independent of the logic of a particular stream
        * how to create?
            ```
            object ZPipeline {
                def map[In, Out](f: In => Out)(implicit trace: Trace): ZPipeline[Any, Nothing, In, Out]
            }
            ```
        * example of usage
            ```
            def via[R1 <: R, E1 >: E, B](pipeline: ZPipeline[R1, E1, A, B]): ZStream[R1, E1, B]

            stream.via(ZPipeline.utf8Decode)
            ```
        * contramap
            * useful when we have a fixed output, and our existing function cannot consume those outputs
            * motivation
                * we have some logic to process a stream already
                * we want to apply logic to stream of different type
            * category theory
                * covariant Functor: map
                    * produce value A
                    * example: covariant Decoder[A]
                        * JSON => A
                * contravariant Functor: contramap
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
                * if the stream potentially fails with an error of type `E` use `pipeToOrFail`
                    * fails with the error of the first channel without passing it through to the second channel
                    * example: `stream.channel.pipeToOrFail(sink.channel)`
            * it can receive exactly one done value (`-InDone`) of type Any
                * elements might be produced asynchronously
                    * sink needs some way to know that there would not be more elements in the future
            * no inputs for its error type (`-InErr`)
                * sinks are not strategies for handling errors
            * sink will eventually terminate, if at all
                * with either a summary value of type `+OutElem`
                * or an error of type `+OutErr`
        * `type ZPipeline[-R, +E, -In, +Out] = ZChannel[R, Nothing, Chunk[In], Any, E, Chunk[Out], Any]`
            * the reason why we can hook sink with pipeline
                * if a pipeline was just a function we would have very limited ability to compose it with other
                streaming data types
            * result of combining a pipeline with a sink/stream is a new sink/stream
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
    * groupByKey
        * example
            ```
            stream.groupByKey(_.key) { case (key, stream) => operations on stream}
            ```
        * function will helpfully push entries with the same key to their own sub-stream
            * reason: are potentially infinite
        * `apply` function of groupBy/groupByKey: 
            * already using flatMapPar under the hood
    * many operators have effectual variants (ZIO suffix)
        * for effectual variants - many have parallel variants (Par suffix)
        * example: map, mapZIO, mapZIOPar
        * digression
            * problem: enters your 100mb/sec production stream of ~200'000 messages each second, and suddenly your app 
              can’t keep up even though its CPU usage seems desperately low.
            * reason: absence of parallelism
                * if you’ve expressed your logic using .map or .flatMap only on your stream, well, that particular 
                  stage of your processing pipeline is guaranteed to be run on a single fiber
            * solution: replace the mapM/flatMap that where applying the business logic to the stream with 
              mapMPar (or some variants)
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
