import sbt._

object Dependencies {
  private val sstBundleMonixHttp4sBlaze = "com.avast" %% "sst-bundle-monix-http4s-blaze" % "0.15.7"
  private val logbackClassic = "ch.qos.logback" % "logback-classic" % "1.2.3"
  private val scalaTest = "org.scalatest" %% "scalatest" % "3.2.3"
  private val mockito = "org.scalatestplus" %% "mockito-4-5" % "3.2.12.0"
  private val sstFlywayPureConfig = "com.avast" %% "sst-flyway-pureconfig" % Versions.sst
  private val sstHttp4sClientBlazePureConfig = "com.avast" %% "sst-http4s-client-blaze-pureconfig" % Versions.sst
  private val sstHttp4sClientMonixCatcap = "com.avast" %% "sst-http4s-client-monix-catnap" % Versions.sst
  private val sstJvm = "com.avast" %% "sst-jvm" % Versions.sst
  private val sstMicrometerJmxPureConfig = "com.avast" %% "sst-micrometer-jmx-pureconfig" % Versions.sst
  private val sstMonixCatnapPureConfig = "com.avast" %% "sst-monix-catnap-pureconfig" % Versions.sst
  private val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testContainers
  private val testMongoContainer = "com.dimafeng" %% "testcontainers-scala-mongodb" % Versions.testContainers
  private val uPickle = "com.lihaoyi" %% "upickle" % "1.5.0"
  private val collectionContrib = "org.scala-lang.modules" %% "scala-collection-contrib" % "0.2.2"
  private val tsec = "io.github.jmcardon" %% "tsec-http4s" % "0.2.0"
  private val http4sCirce = "org.http4s" %% "http4s-circe" % "0.22.12"
  private val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  private val circeLiteral = "io.circe" %% "circe-literal" % Versions.circe
  private val tsecPassword = "io.github.jmcardon" %% "tsec-password" % "0.2.0"
  private val cryptoBits = "org.reactormonk" %% "cryptobits" % "1.3"
  private val monixMongo = "io.monix" %% "monix-mongodb" % "0.6.2"
  private val scalaCacheCore =  "com.github.cb372" %% "scalacache-core" % "0.28.0"
  private val scalaCacheGuava = "com.github.cb372" %% "scalacache-guava" % "0.28.0"
  private val scalaCacheCats = "com.github.cb372" %% "scalacache-cats-effect" % "0.28.0"
  private val scalaCheck = "org.scalacheck" %% "scalacheck" % "1.16.0"

  val scalafixScaluzzi = "com.github.vovapolu" %% "scaluzzi" % "0.1.15"
  val scalafixSortImports = "com.nequissimus" %% "sort-imports" % "0.5.5"
  val silencer = "com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full
  val silencerLib = "com.github.ghik" % "silencer-lib" % Versions.silencer cross CrossVersion.full

  private object Versions {
    val sst = "0.3.3"
    val silencer = "1.7.8"
    val testContainers = "0.40.6"
    val circe = "0.14.1"
  }

  private val CommonTestDependencies = Seq(
    scalaTest % Test,
    testContainers % Test,
    testMongoContainer % Test,
    scalaCheck % Test,
    mockito % Test
  )

  private val CommonDependencies = Seq(
    sstBundleMonixHttp4sBlaze,
    logbackClassic,
    sstHttp4sClientBlazePureConfig,
    sstHttp4sClientMonixCatcap,
    sstJvm,
    collectionContrib,
    http4sCirce,
    circeGeneric,
    circeLiteral,
    circeLiteral,
    monixMongo
  ) ++ CommonTestDependencies

  val bootstapDependencies: Seq[ModuleID] = Seq(
    sstFlywayPureConfig,
    sstMicrometerJmxPureConfig,
    sstMonixCatnapPureConfig,
    uPickle
  ) ++ CommonDependencies

  val dbDependencies: Seq[ModuleID] = CommonDependencies

  val gatewayDependencies: Seq[ModuleID] = CommonDependencies

  val loadBalancerDependencies: Seq[ModuleID] = CommonDependencies

  val participantDependencies: Seq[ModuleID] = CommonDependencies

  val routesDependencies: Seq[ModuleID] = Seq(
    uPickle
  ) ++ CommonDependencies

  val sequenceDependencies: Seq[ModuleID] = CommonDependencies

  val userDependencies: Seq[ModuleID] = Seq(
    tsec,
    tsecPassword,
    cryptoBits,
    scalaCacheCore,
    scalaCacheGuava,
    scalaCacheCats
  ) ++ CommonDependencies

  val utilsDependencies: Seq[ModuleID] = CommonDependencies
}
