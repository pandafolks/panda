package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.RoutesRouting.RoutesAndPrefixesModificationResult
import com.github.pandafolks.panda.routes.dto.{MapperRecordDto, MappingDto, RoutesResourceDto}
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.RoutesResultParser.{parseErrors, parseSuccessfulResults}
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
    case _@GET -> Root / API_NAME / API_VERSION_1 / "routes" as _ => Ok(routesService.findAll())

    case req@POST -> Root / API_NAME / API_VERSION_1 / "routes" as _ =>
      for {
        dto <- req.req.as[RoutesResourceDto]
        saveResults <- routesService.saveRoutes(dto)
        routesSuccessfullySaved = parseSuccessfulResults(saveResults._1)
        groupPrefixesSuccessfullySaved = parseSuccessfulResults(saveResults._2)

        response <- Ok(
          RoutesAndPrefixesModificationResult(
            message = s"Created successfully ${routesSuccessfullySaved.size} routes out of ${saveResults._1.size} requested " +
              s"and ${groupPrefixesSuccessfullySaved.size} prefixes out of ${saveResults._2.size} requested",
            successfulRoutes = routesSuccessfullySaved,
            successfulGroupPrefixes = groupPrefixesSuccessfullySaved,
            routesErrors = parseErrors(saveResults._1),
            groupPrefixesErrors = parseErrors(saveResults._2)
          )
        )
      } yield response
  }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes


  implicit val routesResourceDtoDecoder: EntityDecoder[Task, RoutesResourceDto] = jsonOf[Task, RoutesResourceDto]
  implicit val routesResourceDtoEncoder: EntityEncoder[Task, RoutesResourceDto] = jsonEncoderOf[Task, RoutesResourceDto]

  implicit val mapperRecordDtoDecoder: EntityDecoder[Task, MapperRecordDto] = jsonOf[Task, MapperRecordDto]
  implicit val mapperRecordDtoEncoder: EntityEncoder[Task, MapperRecordDto] = jsonEncoderOf[Task, MapperRecordDto]

  implicit val encodeIntOrString: Encoder[MappingDto] = Encoder.instance(_.value.fold(_.asJson, _.asJson))

  implicit val decodeIntOrString: Decoder[MappingDto] =
    Decoder[String].map(v => MappingDto(Left(v))).or(Decoder[Map[String, MappingDto]].map(v => MappingDto(Right(v))))

  implicit val routesAndPrefixesModificationResultEncoder: EntityEncoder[Task, RoutesAndPrefixesModificationResult] = jsonEncoderOf[Task, RoutesAndPrefixesModificationResult]

}

object RoutesRouting {

  final case class RoutesAndPrefixesModificationResult(message: String,
                                                       successfulRoutes: List[String],
                                                       successfulGroupPrefixes: List[String],
                                                       routesErrors: List[String],
                                                       groupPrefixesErrors: List[String]
                                                      )

}
