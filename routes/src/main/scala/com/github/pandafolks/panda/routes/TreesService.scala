package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.RoutesTree.RouteInfo
import org.http4s.Uri.Path
import org.http4s.Method
import monix.eval.Task

trait TreesService {

  def findRoute(path: Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]]

  def findStandaloneRoute(path: Path, method: Method): Task[Option[(RouteInfo, Map[String, String])]]

  def findPrefix(group: Group): Task[Path]

}
