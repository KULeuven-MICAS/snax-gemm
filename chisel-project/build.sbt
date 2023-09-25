// See README.md for license details.

ThisBuild / scalaVersion     := "2.13.8"
ThisBuild / version          := "0.1.0"
ThisBuild / organization     := "%ORGANIZATION%"

val chiselVersion = "5.0.0"
// val chiselVersion = "3.5.6"

lazy val root = (project in file("."))
  .settings(
    name := "%NAME%",
    libraryDependencies ++= Seq(
      // "edu.berkeley.cs" %% "chisel3" % "3.2-SNAPSHOT",
      "org.chipsalliance" %% "chisel" % chiselVersion,
      "edu.berkeley.cs" %% "chiseltest" % "5.0.0" % "test"
    ),
    scalacOptions ++= Seq(
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      "-Ymacro-annotations",
      "-P:chiselplugin:genBundleElements",
    ),
    addCompilerPlugin("org.chipsalliance" % "chisel-plugin" % chiselVersion cross CrossVersion.full),
  )

// def scalacOptionsVersion(scalaVersion: String): Seq[String] = {
// Seq() ++ {
// // If we're building with Scala > 2.11, enable the compile option
// // switch to support our anonymous Bundle definitions:
// // https://github.com/scala/bug/issues/10047
// CrossVersion.partialVersion(scalaVersion) match {
// case Some((2, scalaMajor: Long)) if scalaMajor < 12 => Seq()
// case _ => Seq("-Xsource:2.11")
// }
// }
// }

// def javacOptionsVersion(scalaVersion: String): Seq[String] = {
// Seq() ++ {
// // Scala 2.12 requires Java 8. We continue to generate
// // Java 7 compatible code for Scala 2.11
// // for compatibility with old clients.
// CrossVersion.partialVersion(scalaVersion) match {
// case Some((2, scalaMajor: Long)) if scalaMajor < 12 =>
// Seq("-source", "1.7", "-target", "1.7")
// case _ =>
// Seq("-source", "1.8", "-target", "1.8")
// }
// }
// }

// name := "MyChisel"
// version := "3.2-SNAPSHOT"
// scalaVersion := "2.12.6"
// crossScalaVersions := Seq("2.11.12", "2.12.4")

// resolvers += "My Maven" at "https://raw.githubusercontent.com/sequencer/m2_repository/master"
// // bug fix from https://github.com/freechipsproject/chisel3/wiki/release-notes-17-09-14
// scalacOptions ++= Seq("-Xsource:2.11")

// libraryDependencies += "edu.berkeley.cs" %% "chisel3" % "3.2-SNAPSHOT"
// libraryDependencies += "edu.berkeley.cs" %% "chisel-iotesters" % "1.2.+"
// libraryDependencies += "edu.berkeley.cs" %% "chisel-dot-visualizer" % "0.1-SNAPSHOT"
// libraryDependencies += "edu.berkeley.cs" %% "rocketchip" % "1.2"

// scalacOptions ++= scalacOptionsVersion(scalaVersion.value)
// javacOptions ++= javacOptionsVersion(scalaVersion.value)