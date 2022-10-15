[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

# scala-zio2-zstream-workshop

* references
    * https://www.zionomicon.com
    * https://zio.dev/version-1.x/datatypes/stream/zstream/
    * [Zymposium - ZIO Streams Part 1 (The Channel Type)](https://www.youtube.com/watch?v=8hG_UY0Dazw)
    * [Zymposium - ZIO Streams Part 2 (Using Channels)](https://www.youtube.com/watch?v=Pfu8m3XBBuQ)
    * [Zymposium - ZIO Streams Part 3 (Streaming Sandwiches)](https://www.youtube.com/watch?v=SGr7mQ15-Pw)
    * [Zymposium - ZIO Streams - Final Part (Fan In/Fan Out)](https://www.youtube.com/watch?v=3EO0yVf63xI)
    * [ZIO Stream — Part 2 — Sinks!](https://www.youtube.com/watch?v=T5vBs6_W_Xg)
    * https://blog.rockthejvm.com/zio-streams/

## preface
* goals of this workshop
    * introduction to zio streams
        * Stream
        * Sink
        * Pipeline
* workshop task
    * base repo: https://github.com/mtumilowicz/scala-zio2-fs2-refined-newtype-workshop
        * replace fs2 with Zstream

## zstream

* ZIO vs ZStream
    * ZIO - single value
* conceptually
    * ZStream is a ZIO effect that can be evaluated repeatedly
    * effectual iterator
    * collection of potentially infinitely many elements
* under the hood
    * implicit chunking
        ```
        trait ZStream[-R, +E, +O] {
            def process: ZIO[R with Scope, Option[E], Chunk[O]] // motivation: efficiency
        }
        ```
        however, filter and map work on individual O values
* useful functions
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
* running stream steps
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
* components
    * ZStream[R, E, O], an effectual stream
        * requires an environment R
        * may fail with an error E
            * if a stream fails with an error it is not well defined to pull from that stream again
        * succeed with zero or more values of type O
        * pull-based
            * elements are processed by being “pulled through the stream” by the sink
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
* we can rewrite any chained ZStream as ZPipelines + ZSink
    * example
        ```
        stream >>> pipeline1 >>> pipeline2 >>> sink
        ```
    * motivation
        * very complex cases: good to have transformation/consumption as data types
