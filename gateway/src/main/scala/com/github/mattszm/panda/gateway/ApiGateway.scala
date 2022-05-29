package com.github.mattszm.panda.gateway

import monix.eval.Task
import org.http4s.{Request, Response}
import org.http4s.Uri.Path

trait ApiGateway {
  /**
   * Provides the response for the specified request.
   *
   * @param request           Received request in its original form
   * @param requestedPath     Part of the Path that indicates an action
   * @return                  Reply received from the end server
   */
  def ask(request: Request[Task], requestedPath: Path): Task[Response[Task]]
}
