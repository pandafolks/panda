package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.RoutesRouting.{ROUTES_NAME, RoutesAndPrefixesModificationResultPayload}
import com.github.pandafolks.panda.routes.payload.{MapperRecordPayload, MappingPayload, RoutesRemovePayload, RoutesResourcePayload}
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
    case _@GET -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME as _ => Ok(routesService.findAll())

    case req@POST -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME as _ =>
      for {
        payload <- req.req.as[RoutesResourcePayload]
        saveResults <- routesService.save(payload)
        routesSuccessfullySaved = parseSuccessfulResults(saveResults._1)
        groupPrefixesSuccessfullySaved = parseSuccessfulResults(saveResults._2)

        response <- Ok(
          RoutesAndPrefixesModificationResultPayload(
            message = s"Created successfully ${routesSuccessfullySaved.size} routes out of ${saveResults._1.size} requested " +
              s"and ${groupPrefixesSuccessfullySaved.size} prefixes out of ${saveResults._2.size} requested",
            successfulRoutes = routesSuccessfullySaved,
            successfulGroupPrefixes = groupPrefixesSuccessfullySaved,
            routesErrors = parseErrors(saveResults._1),
            groupPrefixesErrors = parseErrors(saveResults._2)
          )
        )
      } yield response

    case req@DELETE -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME as _ =>
      for {
        payload <- req.req.as[RoutesRemovePayload]
        deletionResults <- routesService.delete(payload)
        routesSuccessfullyRemoved = parseSuccessfulResults(deletionResults._1)
        groupPrefixesSuccessfullyRemoved = parseSuccessfulResults(deletionResults._2)

        response <- Ok(
          RoutesAndPrefixesModificationResultPayload(
            message = s"Removed successfully ${routesSuccessfullyRemoved.size} routes out of ${deletionResults._1.size} requested " +
              s"and ${groupPrefixesSuccessfullyRemoved.size} prefixes out of ${deletionResults._2.size} requested",
            successfulRoutes = routesSuccessfullyRemoved,
            successfulGroupPrefixes = groupPrefixesSuccessfullyRemoved,
            routesErrors = parseErrors(deletionResults._1),
            groupPrefixesErrors = parseErrors(deletionResults._2)
          )
        )
      } yield response

  }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes


  implicit val routesResourcePayloadDecoder: EntityDecoder[Task, RoutesResourcePayload] = jsonOf[Task, RoutesResourcePayload]
  implicit val routesResourcePayloadEncoder: EntityEncoder[Task, RoutesResourcePayload] = jsonEncoderOf[Task, RoutesResourcePayload]

  implicit val mapperRecordPayloadDecoder: EntityDecoder[Task, MapperRecordPayload] = jsonOf[Task, MapperRecordPayload]
  implicit val mapperRecordPayloadEncoder: EntityEncoder[Task, MapperRecordPayload] = jsonEncoderOf[Task, MapperRecordPayload]

  implicit val mappingPayloadEncoder: Encoder[MappingPayload] = Encoder.instance(_.value.fold(_.asJson, _.asJson))

  implicit val mappingPayloadDecoder: Decoder[MappingPayload] =
    Decoder[String].map(v => MappingPayload(Left(v))).or(Decoder[Map[String, MappingPayload]].map(v => MappingPayload(Right(v))))

  implicit val routesAndPrefixesModificationResultPayloadEncoder: EntityEncoder[Task, RoutesAndPrefixesModificationResultPayload] = jsonEncoderOf[Task, RoutesAndPrefixesModificationResultPayload]

  implicit val routesRemovePayloadDecoder: EntityDecoder[Task, RoutesRemovePayload] = jsonOf[Task, RoutesRemovePayload]
}

object RoutesRouting {

  final val ROUTES_NAME = "routes"

  final case class RoutesAndPrefixesModificationResultPayload(message: String,
                                                              successfulRoutes: List[String],
                                                              successfulGroupPrefixes: List[String],
                                                              routesErrors: List[String],
                                                              groupPrefixesErrors: List[String]
                                                             )

}
