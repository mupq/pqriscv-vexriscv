ThisBuild / organization := "mupq"

ThisBuild / scalaVersion := "2.11.12"

lazy val pqvexriscv = (project in file("."))
  .settings(
    name := "pqvexriscv",
    version := "0.1",
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    run / connectInput := true,
    outputStrategy := Some(StdoutOutput),
  ).dependsOn(vexRiscv)

lazy val vexRiscv = RootProject(uri("git://github.com/SpinalHDL/VexRiscv#ee36c36fddf84e81a48957e8b4963c2764b27b62"))

fork := true

