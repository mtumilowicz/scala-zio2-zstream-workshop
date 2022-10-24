name := "scala-zio2-zstream-workshop"

version := "0.1"

scalaVersion := "2.13.8"

libraryDependencies += "dev.zio" %% "zio" % "2.0.2"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.2"
libraryDependencies += "eu.timepit" %% "refined" % "0.9.28"
libraryDependencies += "io.estatico" %% "newtype" % "0.4.4"
libraryDependencies += "tf.tofu" %% "derevo-cats" % "0.13.0"
libraryDependencies += "dev.zio" %% "zio-test" % "2.0.2" % "test"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.1.2" % "test"
libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.14.1" % "test"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

scalacOptions += "-Ymacro-annotations"