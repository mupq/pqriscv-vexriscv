ThisBuild / organization := "mupq"

// SpinalHDL v1.6.0 can support newer Scala versions, but VexRiscv only references 2.11.12
ThisBuild / scalaVersion := "2.11.12"

val spinalVersion = "1.6.0"

lazy val pqvexriscv = (project in file("."))
  .settings(
    name := "pqvexriscv",
    version := "0.1",
    libraryDependencies ++= Seq(
      "net.java.dev.jna" % "jna" % "5.7.0",
      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
      compilerPlugin("com.github.spinalhdl" % "spinalhdl-idsl-plugin_2.11" % spinalVersion)
    ),
    run / connectInput := true,
    outputStrategy := Some(StdoutOutput),
  ).dependsOn(vexRiscv)

lazy val vexRiscv = RootProject(uri("git://github.com/SpinalHDL/VexRiscv#5fc4125763a1b66758c387c0abea32e602b2e4e5"))

fork := true

