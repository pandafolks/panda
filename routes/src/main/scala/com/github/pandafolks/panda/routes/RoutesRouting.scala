package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.dto.{MapperRecordDto, Mapping, RoutesResourceDto}
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.SubRouting.{API_NAME, API_VERSION_1}
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder}

final class RoutesRouting(private val routesService: RoutesService) extends Http4sDsl[Task] with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _@GET -> Root / API_NAME / API_VERSION_1 / "routes" as _ => Ok()

    case req@POST -> Root / API_NAME / API_VERSION_1 / "routes" as _ =>
      for {
        dto <- req.req.as[RoutesResourceDto]
        res <- routesService.saveRoutes(dto)
        _ <- Task.now(res)
        response <- Ok()
      } yield response
  }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes


  implicit val routesResourceDtoDecoder: EntityDecoder[Task, RoutesResourceDto] = jsonOf[Task, RoutesResourceDto]
  implicit val routesResourceDtoEncoder: EntityEncoder[Task, RoutesResourceDto] = jsonEncoderOf[Task, RoutesResourceDto]

  implicit val mapperRecordDtoDecoder: EntityDecoder[Task, MapperRecordDto] = jsonOf[Task, MapperRecordDto]
  implicit val mapperRecordDtoEncoder: EntityEncoder[Task, MapperRecordDto] = jsonEncoderOf[Task, MapperRecordDto]

  implicit val encodeIntOrString: Encoder[Mapping] = Encoder.instance(_.value.fold(_.asJson, _.asJson))

  implicit val decodeIntOrString: Decoder[Mapping] =
    Decoder[String].map(v => Mapping(Left(v))).or(Decoder[Map[String, Mapping]].map(v => Mapping(Right(v))))

}
