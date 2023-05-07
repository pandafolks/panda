package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.gateway.ApiGatewayRouting.{HEALTHCHECK_NAME, healthcheckResponsePayload}
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.routing.SubRouting.{API_NAME, API_VERSION_1}
import com.github.pandafolks.panda.utils.routing.SubRoutingWithNoAuth
import io.circe.generic.auto._
import monix.eval.Task
import org.http4s.circe.jsonEncoderOf
import org.http4s.dsl.Http4sDsl
import org.http4s.server.middleware.Logger
import org.http4s.{AuthedRoutes, EntityEncoder, HttpRoutes}
import pandaBuildInfo.BuildInfo

final class ApiGatewayRouting(private val apiGateway: ApiGateway)
    extends Http4sDsl[Task]
    with SubRoutingWithNoAuth
    with SubRoutingWithAuth {
  private val routes = HttpRoutes.of[Task] { case request @ _ -> "gateway" /: requestedPath =>
    apiGateway.ask(request, requestedPath)
  }

  private val routesWithAuth = AuthedRoutes.of[User, Task] { case _ @GET -> Root / API_NAME / API_VERSION_1 / HEALTHCHECK_NAME as _ =>
    Ok(healthcheckResponsePayload)
  }

  private val routesWihLogging: HttpRoutes[Task] = Logger.httpRoutes(logHeaders = true, logBody = false)(routes)

  override def getRoutes: HttpRoutes[Task] = routesWihLogging

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routesWithAuth

}

object ApiGatewayRouting {
  final val HEALTHCHECK_NAME = "healthcheck"

  final case class HealthcheckResponsePayload(
      name: String,
      version: String,
      scalaVersion: String,
      sbtVersion: String,
      javaVersion: String
  )

  implicit val participantEncoder: EntityEncoder[Task, HealthcheckResponsePayload] = jsonEncoderOf[Task, HealthcheckResponsePayload]
  implicit val participantSeqEncoder: EntityEncoder[Task, Seq[HealthcheckResponsePayload]] =
    jsonEncoderOf[Task, Seq[HealthcheckResponsePayload]]

  final val healthcheckResponsePayload = HealthcheckResponsePayload(
    "panda",
    BuildInfo.version,
    BuildInfo.scalaVersion,
    BuildInfo.sbtVersion,
    Runtime.version().toString
  )

}
