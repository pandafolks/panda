Global / onChangedBuildSource := ReloadOnSourceChanges
ThisBuild / turbo := true
ThisBuild / organization := "com.github.mattszm"

lazy val commonSettings = BuildSettings.common ++ Seq(
  libraryDependencies ++= Seq(
    Dependencies.logbackClassic,
    Dependencies.scalaTest % Test,
    Dependencies.testContainers % Test,
  ),
  Test / publishArtifact := false
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.sstHttp4sClientBlazePureConfig,
      Dependencies.sstHttp4sClientMonixCatcap,
      Dependencies.sstMonixCatnapPureConfig,
      Dependencies.sstFlywayPureConfig,
      Dependencies.sstJvm,
      Dependencies.sstMicrometerJmxPureConfig,
      Dependencies.sstBundleMonixHttp4sBlaze,
      Dependencies.uPickle,
      Dependencies.scalaUri,
      Dependencies.collectionContrib,
      Dependencies.http4sCirce,
      Dependencies.circeGeneric,
      Dependencies.circeLiteral,
      Dependencies.tsecPassword,
      Dependencies.cryptoBits,
      Dependencies.monixMongo,
    ),
    name := "panda"
  )

addCommandAlias("checkAll", "; scalafmtSbtCheck; scalafmtCheckAll; compile:scalafix --check; test:scalafix --check; test")
addCommandAlias("fixAll", "; compile:scalafix; test:scalafix; scalafmtSbt; scalafmtAll")
