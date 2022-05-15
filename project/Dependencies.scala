import sbt._

object Dependencies {
  val sstBundleMonixHttp4sBlaze = "com.avast" %% "sst-bundle-monix-http4s-blaze" % "0.15.7"
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
  val collectionContrib = "org.scala-lang.modules" %% "scala-collection-contrib" % "0.2.2"
  val tsec = "io.github.jmcardon" %% "tsec-http4s" % "0.2.0"
  val http4sCirce = "org.http4s" %% "http4s-circe" % "0.22.12"
  val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  val circeLiteral = "io.circe" %% "circe-literal" % Versions.circe
  val circeParser = "io.circe" %% "circe-parser"  % Versions.circe
  val tsecPassword = "io.github.jmcardon" %% "tsec-password" % "0.2.0"
  val cryptoBits = "org.reactormonk" %% "cryptobits" % "1.3"
  val monixMongo = "io.monix" %% "monix-mongodb" % "0.6.2"
  val scalaCacheCore =  "com.github.cb372" %% "scalacache-core" % "0.28.0"
  val scalaCacheGuava = "com.github.cb372" %% "scalacache-guava" % "0.28.0"
  val scalaCacheCats = "com.github.cb372" %% "scalacache-cats-effect" % "0.28.0"

  object Versions {
    val sst = "0.3.3"
    val silencer = "1.7.8"
    val doobie = "0.9.2"
    val testContainers = "0.38.6"
    val circe = "0.14.1"
  }
}
