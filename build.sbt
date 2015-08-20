import com.banno.license.Licenses._
import com.banno.license.Plugin.LicenseKeys._
import com.typesafe.sbt.packager.docker.DockerPlugin.autoImport._
import net.virtualvoid.sbt.graph.Plugin._
import sbt.Keys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

val commonSettings =
  graphSettings ++
  licenseSettings ++
  Seq(
    organization  := "rhttpc",
    scalaVersion  := "2.11.7",
    scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8"),
    license := apache2("Copyright 2015 the original author or authors."),
    removeExistingHeaderBlock := true,
    resolvers ++= Seq(
      "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository",
      "SpinGo OSS" at "http://spingo-oss.s3.amazonaws.com/repositories/releases"
    )
  )

val akkaV = "2.4-M3"
val akkaStreamsV = "1.0"
val amqpcV = "3.5.4"
val json4sV = "3.2.11"
val logbackV = "1.1.3"
val slf4jV = "1.7.7"
val dispatchV = "0.11.3"
val scalaTestV = "3.0.0-M7"
val spingoV = "1.0.0-M16"

lazy val api = (project in file("rhttpc-api")).
  settings(commonSettings).
  settings(
    name := "rhttpc-api",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-http-experimental"        % akkaStreamsV,
        "com.typesafe.akka"       %% "akka-actor"                    % akkaV,
        "com.typesafe.akka"       %% "akka-agent"                    % akkaV,
        "com.rabbitmq"             % "amqp-client"                   % amqpcV,
        "org.json4s"              %% "json4s-native"                 % json4sV,
        "org.scalatest"           %% "scalatest"                     % scalaTestV    % "test"
      )
    }
  )

lazy val client = (project in file("rhttpc-client")).
  settings(commonSettings).
  settings(
    name := "rhttpc-client",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-persistence"              % akkaV,
        "org.iq80.leveldb"          % "leveldb"                      % "0.7",
        "org.fusesource.leveldbjni" % "leveldbjni-all"               % "1.8",
        "org.slf4j"                % "slf4j-api"                     % slf4jV,
        "com.typesafe.akka"       %% "akka-testkit"                  % akkaV         % "test",
        "org.scalatest"           %% "scalatest"                     % scalaTestV    % "test",
        "com.typesafe.akka"       %% "akka-slf4j"                    % akkaV         % "test",
        "ch.qos.logback"           % "logback-classic"               % logbackV      % "test"
      )
    }
  ).
  dependsOn(api)

lazy val proxy = (project in file("rhttpc-proxy")).
  settings(commonSettings).
  enablePlugins(DockerPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    name := "rhttpc-proxy",
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-slf4j"                    % akkaV,
        "ch.qos.logback"           % "logback-classic"               % logbackV
      )
    },
    dockerExposedPorts := Seq(5005),
    dockerEntrypoint := Seq("bin/rhttpc-proxy", "-jvm-debug", "5005")
  ).
  dependsOn(api)

lazy val sampleEcho = (project in file("sample/sample-echo")).
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

lazy val sampleApp = (project in file("sample/sample-app")).
  settings(commonSettings).
  enablePlugins(DockerPlugin).
  enablePlugins(JavaAppPackaging).
  settings(
    libraryDependencies ++= {
      Seq(
        "com.typesafe.akka"       %% "akka-http-experimental"        % akkaStreamsV,
        "com.typesafe.akka"       %% "akka-slf4j"                    % akkaV,
        "ch.qos.logback"           % "logback-classic"               % logbackV,
        "com.typesafe.akka"       %% "akka-testkit"                  % akkaV         % "test",
        "org.scalatest"           %% "scalatest"                     % scalaTestV    % "test"
      )
    },
    dockerExposedPorts := Seq(8081)
  ).
  dependsOn(client)

lazy val testProj = (project in file("sample/test")).
  settings(commonSettings).
  settings(
    libraryDependencies ++= {
      Seq(
        "com.github.docker-java"   % "docker-java"                   % "1.4.0" exclude("commons-logging", "commons-logging"),
        "commons-io"               % "commons-io"                    % "2.4",
        "net.databinder.dispatch" %% "dispatch-core"                 % dispatchV,
        "ch.qos.logback"           %  "logback-classic"              % logbackV,
        "org.scalatest"           %% "scalatest"                     % scalaTestV    % "test"
      )
    },
    Keys.test <<= (Keys.test in Test).dependsOn(
      publishLocal in Docker in proxy,
      publishLocal in Docker in sampleEcho,
      publishLocal in Docker in sampleApp
    )
  )


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
