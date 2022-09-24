Global / onChangedBuildSource := ReloadOnSourceChanges

inThisBuild(List(
  organization := "com.github.pandafolks",
  homepage := Some(url("https://github.com/pandafolks/panda")),
  licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  developers := List(
    Developer(
      "mattszm",
      "Mateusz Szmal",
      "-",
      url("https://github.com/MattSzm")
    )
  )
))

//skip in publish := true

lazy val sharedSettings = Seq(
  scalaVersion       := "2.13.8",
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature",
    "-language:higherKinds",
    "-language:implicitConversions",
    "-language:experimental.macros"
  ),
  scalacOptions in (Compile, console) ++= Seq("-Ywarn-unused:imports"),
  scalacOptions ++= Seq(
    "-Ywarn-unused:imports",
    "-Ywarn-dead-code",
    "-Xlint:adapted-args",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:doc-detached",
    "-Xlint:private-shadow",
    "-Xlint:type-parameter-shadow",
    "-Xlint:poly-implicit-overload",
    "-Xlint:option-implicit",
    "-Xlint:delayedinit-select",
  ),

  // ScalaDoc settings
  scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings"),
  autoAPIMappings := true,
  scalacOptions in ThisBuild ++= Seq(
    "-sourcepath",
    file(".").getAbsolutePath.replaceAll("[.]$", "")
  ),
  parallelExecution in Test             := true,
  parallelExecution in ThisBuild        := true,
  testForkedParallel in Test            := true,
  testForkedParallel in ThisBuild       := true,
  concurrentRestrictions in Global += Tags.limit(Tags.Test, 3),
  logBuffered in Test            := false,
  logBuffered in IntegrationTest := false,
  incOptions := incOptions.value.withLogRecompileOnMacro(false),
  pomIncludeRepository    := { _ => false },

  autoAPIMappings := true,
  Test / publishArtifact := false,

  ThisBuild / scalafixDependencies ++= Seq(
    Dependencies.scalafixScaluzzi,
    Dependencies.scalafixSortImports
  ),
  libraryDependencies ++= Seq(
    compilerPlugin(Dependencies.silencer),
    Dependencies.silencerLib
  ),
)

scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings")

val IT = config("it") extend Test

lazy val panda = (project in file("."))
  .configs(IntegrationTest, IT)
  .settings(sharedSettings)
  .settings(
    name := "panda",
    assembly / mainClass := Some("com.github.pandafolks.panda.bootstrap.App"),
    assembly / assemblyJarName := "panda.jar",
  )
  .aggregate(bootstap, db, gateway, loadBalancer, healthCheck, participant, routes, sequence, user, nodesTracker, utils)
  .dependsOn(bootstap, db, gateway, loadBalancer, healthCheck, participant, routes, sequence, user, nodesTracker, utils)

lazy val bootstap = pandaConnector("bootstap", Dependencies.bootstapDependencies, Seq(db, gateway))
lazy val db = pandaConnector("db", Dependencies.dbDependencies, Seq(user, healthCheck, participant, sequence, nodesTracker))
lazy val gateway = pandaConnector("gateway", Dependencies.gatewayDependencies, Seq(loadBalancer, routes, utils))
lazy val loadBalancer = pandaConnector("loadBalancer", Dependencies.loadBalancerDependencies, Seq(participant, utils, httpClient))
lazy val healthCheck = pandaConnector("healthCheck", Dependencies.healthCheckDependencies, Seq(participant, nodesTracker, httpClient))
lazy val participant = pandaConnector("participant", Dependencies.participantDependencies, Seq(sequence, utils, routes, user))
lazy val routes = pandaConnector("routes", Dependencies.routesDependencies, Seq(user))
lazy val sequence = pandaConnector("sequence", Dependencies.sequenceDependencies, Seq(user))
lazy val user = pandaConnector("user", Dependencies.userDependencies, Seq(utils))
lazy val nodesTracker = pandaConnector("nodesTracker", Dependencies.nodesTrackerDependencies, Seq(utils))
lazy val httpClient = pandaConnector("httpClient", Dependencies.httpClientDependencies)
lazy val utils = pandaConnector("utils", Dependencies.utilsDependencies)

mainClass in (Compile, run) := Some("com.github.pandafolks.panda.bootstrap.App")

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", _*) => MergeStrategy.discard
  case _ => MergeStrategy.first
}

def pandaConnector(
                    moduleName: String,
                    projectDependencies: Seq[ModuleID],
                    dependsOn: Seq[sbt.ClasspathDep[sbt.ProjectReference]] = Seq.empty
                  ): Project = {
  Project(id = moduleName, base = file(moduleName))
    .settings(name := s"panda-$moduleName", libraryDependencies ++= projectDependencies, Defaults.itSettings)
    .settings(sharedSettings)
    .configs(IntegrationTest, IT)
    .dependsOn(dependsOn: _*)
}
