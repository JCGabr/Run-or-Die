ThisBuild / scalaVersion := "3.8.3"

val PekkoVersion = "1.1.5"
val PekkoHttpVersion = "1.3.0"

lazy val shared = crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("shared"))
    .settings(
        libraryDependencies ++= Seq()
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
            ("org.scala-js" %%% "scalajs-dom" % "2.8.0")
        )
    )

lazy val server = project
    .in(file("server"))
    .dependsOn(shared.jvm)
    .settings(
        libraryDependencies ++= Seq(
            "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
            "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
            "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion
        )
    )