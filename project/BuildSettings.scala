import sbt.Keys._
import sbt._
import scalafix.sbt.ScalafixPlugin.autoImport._

object BuildSettings {

  lazy val common: Seq[Def.Setting[_]] = Seq(
    scalaVersion := "2.13.8",
    libraryDependencies ++= Seq(
      compilerPlugin(Dependencies.silencer),
      Dependencies.silencerLib
    ),
    ThisBuild / scalafixDependencies ++= Seq(
      Dependencies.scalafixScaluzzi,
      Dependencies.scalafixSortImports
    ),
    Test / publishArtifact := false
  )

}
