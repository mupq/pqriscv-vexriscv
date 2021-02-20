ThisBuild / organization := "mupq"

ThisBuild / scalaVersion := "2.11.12"

lazy val pqvexriscv = (project in file("."))
  .settings(
    name := "pqvexriscv",
    version := "0.1",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % "1.4.0")
    ),
    run / connectInput := true,
    outputStrategy := Some(StdoutOutput),
  ).dependsOn(vexRiscv)

lazy val vexRiscv = RootProject(uri("git://github.com/SpinalHDL/VexRiscv#36b3cd918896c94c4e8a224d97c559ab6dbf3ec9"))

fork := true

