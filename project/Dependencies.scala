import sbt._

object Dependencies {
  val sstBundleMonixHttp4sBlaze = "com.avast" %% "sst-bundle-monix-http4s-blaze" % "0.15.7"
  val kindProjector = "org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full
  val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3"
  val scalafixScaluzzi = "com.github.vovapolu" %% "scaluzzi" % "0.1.15"
  val scalafixSortImports = "com.nequissimus" %% "sort-imports" % "0.5.5"
  val silencer = "com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full
  val silencerLib = "com.github.ghik" % "silencer-lib" % Versions.silencer cross CrossVersion.full
  val sstFlywayPureConfig = "com.avast" %% "sst-flyway-pureconfig" % Versions.sst
  val sstHttp4sClientBlazePureConfig = "com.avast" %% "sst-http4s-client-blaze-pureconfig" % Versions.sst
  val sstHttp4sClientMonixCatcap = "com.avast" %% "sst-http4s-client-monix-catnap" % Versions.sst
  val sstJvm = "com.avast" %% "sst-jvm" % Versions.sst
  val sstMicrometerJmxPureConfig = "com.avast" %% "sst-micrometer-jmx-pureconfig" % Versions.sst
  val sstMonixCatnapPureConfig = "com.avast" %% "sst-monix-catnap-pureconfig" % Versions.sst
  val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testContainers
  val uPickle = "com.lihaoyi" %% "upickle" % "1.5.0"
  val scalaUri = "io.lemonlabs" %% "scala-uri" % "4.0.2"

  object Versions {
    val sst = "0.3.3"
    val silencer = "1.7.1"
    val doobie = "0.9.2"
    val testContainers = "0.38.6"
  }

}
