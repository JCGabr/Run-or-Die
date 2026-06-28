ThisBuild / scalaVersion := "3.8.3"

val PekkoVersion = "1.1.5"
val PekkoHttpVersion = "1.3.0"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("shared"))
    .settings(
        libraryDependencies ++= Seq(
            "com.lihaoyi" %%% "upickle" % "3.3.1"
        )
    )

lazy val client = project
    .in(file("client"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(shared.js)
    .settings(
        scalaJSUseMainModuleInitializer := true,
        scalaJSLinkerConfig ~= {_.withModuleKind(ModuleKind.ESModule)},

        // Library Maven Central org.scala-js
        libraryDependencies ++= Seq(
            ("org.scala-js" %%% "scalajs-dom" % "2.8.0"),
            "org.typelevel" %%% "cats-effect" % "3.7.0"
        )
    )

lazy val server = project
    .in(file("server"))
    .dependsOn(shared.jvm)
    .settings(
        libraryDependencies ++= Seq(
            "org.typelevel" %% "cats-effect" % "3.7.0",
            "co.fs2" %% "fs2-core" % "3.13.0",
            "org.http4s" %% "http4s-ember-server" % "0.23.34",
            "org.http4s" %% "http4s-dsl" % "0.23.34"
        )
    )
