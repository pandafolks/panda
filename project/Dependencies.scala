import sbt._

object Dependencies {
  private val sstBundleMonixHttp4sBlaze = "com.avast" %% "sst-bundle-monix-http4s-blaze" % Versions.sst
  private val sstHttp4sClientBlaze = "com.avast" %% "sst-http4s-client-blaze" % Versions.sst
  private val logbackClassic = "ch.qos.logback" % "logback-classic" % Versions.logback
  private val scalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTest
  private val mockito = "org.scalatestplus" %% "mockito-4-5" % Versions.mockito
  private val testContainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testContainers
  private val testMongoContainer = "com.dimafeng" %% "testcontainers-scala-mongodb" % Versions.testContainers
  private val collectionContrib = "org.scala-lang.modules" %% "scala-collection-contrib" % Versions.collectionContrib
  private val tsec = "io.github.jmcardon" %% "tsec-http4s" % Versions.tsec
  private val http4sCirce = "org.http4s" %% "http4s-circe" % "0.22.14" // pay attention - needs to be in alignment with the version present in sstBundleMonixHttp4sBlaze
  private val circeGeneric = "io.circe" %% "circe-generic" % Versions.circe
  private val circeLiteral = "io.circe" %% "circe-literal" % Versions.circe
  private val tsecPassword = "io.github.jmcardon" %% "tsec-password" % Versions.tsec
  private val cryptoBits = "org.reactormonk" %% "cryptobits" % Versions.cryptoBits
  private val monixMongo = "io.monix" %% "monix-mongodb" % "0.6.2"
  private val scalaCacheCore =  "com.github.cb372" %% "scalacache-core" % Versions.scalaCache
  private val scalaCacheGuava = "com.github.cb372" %% "scalacache-guava" % Versions.scalaCache
  private val scalaCacheCats = "com.github.cb372" %% "scalacache-cats-effect" % Versions.scalaCache
  private val scalaCheck = "org.scalacheck" %% "scalacheck" % Versions.scalaCheck

  val scalafixScaluzzi = "com.github.vovapolu" %% "scaluzzi" % Versions.scaluzzi
  val scalafixSortImports = "com.nequissimus" %% "sort-imports" % Versions.sortImports
  val silencer = "com.github.ghik" % "silencer-plugin" % Versions.silencer cross CrossVersion.full
  val silencerLib = "com.github.ghik" % "silencer-lib" % Versions.silencer cross CrossVersion.full

  private object Versions {
    val sst = "0.16.3" // http4s - 0.22.14
    val silencer = "1.7.8"
    val testContainers = "0.40.6"
    val circe = "0.14.1"
    val scalaCache = "0.28.0"
    val logback = "1.2.11"
    val scalaTest = "3.2.3"
    val mockito = "3.2.12.0"
    val collectionContrib = "0.2.2"
    val tsec = "0.2.0"
    val cryptoBits = "1.3"
    val scalaCheck = "1.16.0"
    val scaluzzi = "0.1.15"
    val sortImports = "0.5.5"
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
    sstHttp4sClientBlaze,
    logbackClassic,
    collectionContrib,
    http4sCirce,
    circeGeneric,
    circeLiteral,
    circeLiteral,
    monixMongo
  ) ++ CommonTestDependencies

  val bootstapDependencies: Seq[ModuleID] = CommonDependencies

  val dbDependencies: Seq[ModuleID] = CommonDependencies

  val gatewayDependencies: Seq[ModuleID] = CommonDependencies

  val healthCheckDependencies: Seq[ModuleID] = CommonDependencies

  val loadBalancerDependencies: Seq[ModuleID] = CommonDependencies

  val participantDependencies: Seq[ModuleID] = CommonDependencies

  val routesDependencies: Seq[ModuleID] = CommonDependencies

  val sequenceDependencies: Seq[ModuleID] = CommonDependencies

  val userDependencies: Seq[ModuleID] = Seq(
    tsec,
    tsecPassword,
    cryptoBits
  ) ++ CommonDependencies

  val nodesTrackerDependencies: Seq[ModuleID] = CommonDependencies

  val utilsDependencies: Seq[ModuleID] = Seq(
    scalaCacheCore,
    scalaCacheGuava,
    scalaCacheCats
  ) ++ CommonDependencies
}
