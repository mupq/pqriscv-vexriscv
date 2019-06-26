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

lazy val vexRiscv = RootProject(uri("git://github.com/SpinalHDL/VexRiscv#20cbd4012fc69821b0dccbcdb1af63ea72ba1d56"))

fork := true

