import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import net.virtualvoid.sbt.graph.Plugin._
import com.banno.license.Plugin.LicenseKeys._
import com.banno.license.Licenses._
import ReleaseTransformations._
import com.typesafe.sbt.packager.docker._
import sbt.Keys._

val commonSettings =
  graphSettings ++
  licenseSettings ++
  Seq(
    organization  := "org.github",
    scalaVersion  := "2.11.7",
    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    license := apache2("Copyright 2015 the original author or authors."),
    removeExistingHeaderBlock := true,
    resolvers ++= Seq(
      "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      Resolver.sonatypeRepo("snapshots")
    )
  )

val akkaV = "2.3.12"
val akkaStreamsV = "1.0"
val json4sV = "3.2.11"
val logbackV = "1.1.3"
val dispatchV = "0.11.3"
val scalaTestV = "2.2.5"

lazy val client = (project in file("client")).
  settings(commonSettings).
  settings(
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-persistence-experimental" % akkaV,
        "org.json4s"              %% "json4s-native"                 % json4sV
      )
    }
  )

lazy val server = (project in file("server")).
  settings(commonSettings).
  settings(
    libraryDependencies ++= {
      Seq(
      )
    }
  )

lazy val sampleEcho = (project in file("sample-echo")).
  settings(commonSettings).
  enablePlugins(DockerPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-http-experimental"        % akkaStreamsV,
        "com.typesafe.akka"       %% "akka-slf4j"                    % akkaV,
        "ch.qos.logback"           %  "logback-classic"              % logbackV
      )
    },
    dockerExposedPorts := Seq(8082)
  )

lazy val sampleApp = (project in file("sample-app")).
  settings(commonSettings).
  enablePlugins(DockerPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-http-experimental"        % akkaStreamsV,
        "net.databinder.dispatch" %% "dispatch-core"                 % dispatchV,
        "com.typesafe.akka"       %% "akka-slf4j"                    % akkaV,
        "ch.qos.logback"           % "logback-classic"               % logbackV,
        "com.typesafe.akka"       %% "akka-testkit"                  % akkaV         % "test",
        "org.scalatest"           %% "scalatest"                     % scalaTestV    % "test"
      )
    },
    dockerExposedPorts := Seq(8081)
  ).
  dependsOn(client)

val playV = "2.3.9"

lazy val test = (project in file("test")).
  settings(commonSettings).
  settings(
    libraryDependencies ++= {
      Seq(
        "net.databinder.dispatch" %% "dispatch-core"                 % dispatchV,
        "org.almoehi"             %% "reactive-docker"               % "0.1-SNAPSHOT",
        "com.typesafe.play"       %% "play-iteratees"                % playV, // reactive-docker dependency with accessible version
        "com.typesafe.play"       %% "play-json"                     % playV  // reactive-docker dependency with accessible version
      )
    }
  ).
  dependsOn(sampleApp, sampleEcho)

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,              // : ReleaseStep
  inquireVersions,                        // : ReleaseStep
  runTest,                                // : ReleaseStep
  setReleaseVersion,                      // : ReleaseStep
  commitReleaseVersion,                   // : ReleaseStep, performs the initial git checks
  tagRelease,                             // : ReleaseStep
  setNextVersion,                         // : ReleaseStep
  commitNextVersion,                      // : ReleaseStep
  pushChanges                             // : ReleaseStep, also checks that an upstream branch is properly configured
)
