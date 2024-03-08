ThisBuild / organization := "mupq"

// SpinalHDL can support newer Scala versions, but VexRiscv only references 2.11.12
ThisBuild / scalaVersion := "2.11.12"

val spinalVersion = "1.10.1"

lazy val pqvexriscv = (project in file("."))
  .settings(
    name := "pqvexriscv",
    version := "0.1",
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.14.0",
      "org.scalatest" %% "scalatest" % "3.2.5" % "test", // Same version as VexRiscv uses...
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion)
    ),
    run / connectInput := true,
    outputStrategy := Some(StdoutOutput),
  ).dependsOn(vexRiscv)

// Newer versions of VexRiscv bring a new debugger, that requires more refactoring
lazy val vexRiscv = RootProject(uri("https://github.com/SpinalHDL/VexRiscv.git#e52251d88c1d0dd397cc976654b291909152a979"))

fork := true

