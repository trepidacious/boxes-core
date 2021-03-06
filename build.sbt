name := "boxes-core"

version := "0.1-SNAPSHOT"

organization := "org.rebeam"

scalaVersion := "2.11.7"

resolvers ++= Seq(
  "jcenter" at "http://jcenter.bintray.com",
  Resolver.sonatypeRepo("releases"),
  Resolver.sonatypeRepo("snapshots")
)

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
  "org.scalacheck" %% "scalacheck" % "1.11.3" % "test",  //Note that this is NOT the most recent version of scalacheck,
                                                        //but IS the one referenced by scalatest on github
  "org.scalaz" %% "scalaz-core" % "7.1.2",
  "org.scalaz.stream" %% "scalaz-stream" % "0.7.2a",
  "com.google.protobuf" % "protobuf-java" % "2.6.1"
)

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked",
  "-Xfatal-warnings",
  "-Xlint"
)

// testOptions in Test += Tests.Argument("-oDF")

//Run tests in sequence since they use Shelf operations that should not be interleaved.
parallelExecution in Test := false