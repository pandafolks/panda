package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.routes.Group
import monix.eval.Task
import org.http4s.{Request, Response}
import org.http4s.Uri.Path

trait LoadBalancer {
  def route(request: Request[Task], requestedPath: Path, group: Group): Task[Response[Task]]
}
