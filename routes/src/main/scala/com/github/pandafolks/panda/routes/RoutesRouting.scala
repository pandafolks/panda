package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.{MapperRecordDto, RoutesResourceDto}
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.SubRouting.{API_NAME, API_VERSION_1}
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import io.circe.generic.auto._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder}

final class RoutesRouting(private val routesService: RoutesService) extends Http4sDsl[Task] with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _@GET -> Root / API_NAME / API_VERSION_1 / "routes" as _ => Ok()

    case req@POST -> Root / API_NAME / API_VERSION_1 / "routes" as _ =>
      for {
        dtos <- req.req.as[RoutesResourceDto]
        _ <- Task.now(dtos)
        response <- Ok()
      } yield response
  }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes


  implicit val routesResourceDtoDecoder: EntityDecoder[Task, RoutesResourceDto] = jsonOf[Task, RoutesResourceDto]
  implicit val routesResourceDtoEncoder: EntityEncoder[Task, RoutesResourceDto] = jsonEncoderOf[Task, RoutesResourceDto]

  implicit val mapperRecordDtoDecoder: EntityDecoder[Task, MapperRecordDto] = jsonOf[Task, MapperRecordDto]
  implicit val mapperRecordDtoEncoder: EntityEncoder[Task, MapperRecordDto] = jsonEncoderOf[Task, MapperRecordDto]

}
