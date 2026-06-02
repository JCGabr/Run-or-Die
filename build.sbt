val scalaVersionUsed = "3.8.3"

val AkkaVersion = "2.10.11"
val AkkaHttpVersion = "10.7.4"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("shared"))
    .settings(
        scalaVersion := scalaVersionUsed,
        libraryDependencies ++= Seq()
    )

lazy val client = project
    .in(file("client"))
    .enablePlugins(ScalaJSPlugin)
    .dependsOn(shared.js)
    .settings(
        scalaVersion := scalaVersionUsed,
        scalaJSUseMainModuleInitializer := true,
        scalaJSLinkerConfig ~= {_.withModuleKind(ModuleKind.ESModule)},

        // Library Maven Central org.scala-js
        libraryDependencies ++= Seq(
            ("org.scala-js" %%% "scalajs-dom" % "2.8.0")
        )
    )

lazy val server = project
    .in(file("server"))
    .dependsOn(shared.jvm)
    .settings(
        scalaVersion := scalaVersionUsed,
        libraryDependencies ++= Seq(
            "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
            "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
            "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
        )
    )