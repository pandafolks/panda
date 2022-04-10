package com.mattszm.panda.gateway

import monix.eval.Task
import org.http4s.{Request, Response}
import org.http4s.Uri.Path

trait ApiGateway {
  def getResponse(request: Request[Task], requestedPath: Path): Task[Response[Task]]
}
