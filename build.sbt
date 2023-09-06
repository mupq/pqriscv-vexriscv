ThisBuild / organization := "mupq"

// SpinalHDL v1.8.0 can support newer Scala versions, but VexRiscv only references 2.11.12
ThisBuild / scalaVersion := "2.11.12"

val spinalVersion = "1.8.0"

lazy val pqvexriscv = (project in file("."))
  .settings(
    name := "pqvexriscv",
    version := "0.1",
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.13.0",
      "org.scalatest" %% "scalatest" % "3.2.5" % "test",
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion)
    ),
    run / connectInput := true,
    outputStrategy := Some(StdoutOutput),
  ).dependsOn(vexRiscv)

// Newer versions of VexRiscv bring a new debugger, that requires more refactoring
lazy val vexRiscv = RootProject(uri("https://github.com/SpinalHDL/VexRiscv.git#b29eb542f278edb8ee2c91863a5b26e76b5932c9"))

fork := true

