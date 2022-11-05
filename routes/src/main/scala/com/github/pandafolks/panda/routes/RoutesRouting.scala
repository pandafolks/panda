package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.RoutesRouting.{
  MAPPERS_NAME,
  OVERRIDE_KEY_WORD,
  PREFIXES_NAME,
  ROUTES_NAME,
  RoutesAndPrefixesModificationResultPayload
}
import com.github.pandafolks.panda.routes.filter.StandaloneFilter
import com.github.pandafolks.panda.routes.payload.{
  MapperRecordPayload,
  MappingPayload,
  RoutesRemovePayload,
  RoutesResourcePayload
}
import com.github.pandafolks.panda.user.{SubRoutingWithAuth, User}
import com.github.pandafolks.panda.utils.PersistenceError
import com.github.pandafolks.panda.utils.routing.RoutesResultParser.{parseErrors, parseSuccessfulResults}
import com.github.pandafolks.panda.utils.routing.SubRouting.{API_NAME, API_VERSION_1}
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedRoutes, EntityDecoder, EntityEncoder, QueryParamDecoder}

final class RoutesRouting(private val routesService: RoutesService) extends Http4sDsl[Task] with SubRoutingWithAuth {

  private val routes = AuthedRoutes.of[User, Task] {
    case _ @GET -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME
        :? OptionalGroupFilterQueryParamMatcher(maybeGroup)
        :? OptionalStandaloneFilterQueryParamMatcher(maybeStandalone)
        as _ =>
      Ok(
        maybeGroup
          .map(group => routesService.findByGroup(group, StandaloneFilter.getFromOptional(maybeStandalone)))
          .getOrElse(routesService.findAll(StandaloneFilter.getFromOptional(maybeStandalone)))
      )

    case _ @GET -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME / MAPPERS_NAME
        :? OptionalStandaloneFilterQueryParamMatcher(maybeStandalone)
        as _ =>
      Ok(routesService.findAllMappers(StandaloneFilter.getFromOptional(maybeStandalone)))

    case _ @GET -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME / PREFIXES_NAME as _ =>
      Ok(routesService.findAllPrefixes())

    case req @ POST -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME as _ =>
      for {
        payload <- req.req.as[RoutesResourcePayload]
        saveResults <- routesService.save(payload)
        response <- Ok(RoutesAndPrefixesModificationResultPayload.createSaveResponsePayload(saveResults))
      } yield response

    case req @ POST -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME / OVERRIDE_KEY_WORD as _ =>
      for {
        payload <- req.req.as[RoutesResourcePayload]
        saveResults <- routesService.saveWithOverrides(payload)
        response <- Ok(RoutesAndPrefixesModificationResultPayload.createSaveResponsePayload(saveResults))
      } yield response

    case req @ DELETE -> Root / API_NAME / API_VERSION_1 / ROUTES_NAME as _ =>
      for {
        payload <- req.req.as[RoutesRemovePayload]
        deletionResults <- routesService.delete(payload)
        response <- Ok(RoutesAndPrefixesModificationResultPayload.createDeleteResponsePayload(deletionResults))
      } yield response

  }

  override def getRoutesWithAuth: AuthedRoutes[User, Task] = routes

  // Encoders and Decoders:
  implicit val routesResourcePayloadDecoder: EntityDecoder[Task, RoutesResourcePayload] =
    jsonOf[Task, RoutesResourcePayload]
  implicit val routesResourcePayloadEncoder: EntityEncoder[Task, RoutesResourcePayload] =
    jsonEncoderOf[Task, RoutesResourcePayload]

  implicit val mapperRecordPayloadDecoder: EntityDecoder[Task, MapperRecordPayload] = jsonOf[Task, MapperRecordPayload]
  implicit val mapperRecordPayloadEncoder: EntityEncoder[Task, MapperRecordPayload] =
    jsonEncoderOf[Task, MapperRecordPayload]

  implicit val mappersListEncoder: EntityEncoder[Task, List[(String, MapperRecordPayload)]] =
    jsonEncoderOf[Task, List[(String, MapperRecordPayload)]]
  implicit val prefixesMapEncoder: EntityEncoder[Task, Map[String, String]] = jsonEncoderOf[Task, Map[String, String]]

  implicit val mappingPayloadEncoder: Encoder[MappingPayload] = Encoder.instance(_.value.fold(_.asJson, _.asJson))
  implicit val mappingPayloadDecoder: Decoder[MappingPayload] =
    Decoder[String]
      .map(v => MappingPayload(Left(v)))
      .or(Decoder[Map[String, MappingPayload]].map(v => MappingPayload(Right(v))))

  implicit val routesAndPrefixesModificationResultPayloadEncoder
      : EntityEncoder[Task, RoutesAndPrefixesModificationResultPayload] =
    jsonEncoderOf[Task, RoutesAndPrefixesModificationResultPayload]
  implicit val routesRemovePayloadDecoder: EntityDecoder[Task, RoutesRemovePayload] = jsonOf[Task, RoutesRemovePayload]

  // Filters:
  object OptionalGroupFilterQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("group")

  implicit val filterStandaloneParamDecoder: QueryParamDecoder[StandaloneFilter] = QueryParamDecoder[Boolean].map {
    case true  => StandaloneFilter.StandaloneOnly
    case false => StandaloneFilter.NonStandaloneOnly
  }

  object OptionalStandaloneFilterQueryParamMatcher
      extends OptionalQueryParamDecoderMatcher[StandaloneFilter]("standalone")

}

object RoutesRouting {

  final val ROUTES_NAME = "routes"
  final val MAPPERS_NAME = "mappers"
  final val PREFIXES_NAME = "prefixes"
  final val OVERRIDE_KEY_WORD = "override"

  final case class RoutesAndPrefixesModificationResultPayload(
      message: String,
      successfulRoutes: List[String],
      successfulGroupPrefixes: List[String],
      routesErrors: List[String],
      groupPrefixesErrors: List[String]
  )

  object RoutesAndPrefixesModificationResultPayload {
    def createSaveResponsePayload(
        saveResults: (List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])
    ): RoutesAndPrefixesModificationResultPayload = {
      val routesSuccessfullySaved = parseSuccessfulResults(saveResults._1)
      val groupPrefixesSuccessfullySaved = parseSuccessfulResults(saveResults._2)

      RoutesAndPrefixesModificationResultPayload(
        message =
          s"Created successfully ${routesSuccessfullySaved.size} routes out of ${saveResults._1.size} requested " +
            s"and ${groupPrefixesSuccessfullySaved.size} prefixes out of ${saveResults._2.size} requested",
        successfulRoutes = routesSuccessfullySaved,
        successfulGroupPrefixes = groupPrefixesSuccessfullySaved,
        routesErrors = parseErrors(saveResults._1),
        groupPrefixesErrors = parseErrors(saveResults._2)
      )
    }

    def createDeleteResponsePayload(
        deletionResults: (List[Either[PersistenceError, String]], List[Either[PersistenceError, String]])
    ): RoutesAndPrefixesModificationResultPayload = {
      val routesSuccessfullyRemoved = parseSuccessfulResults(deletionResults._1)
      val groupPrefixesSuccessfullyRemoved = parseSuccessfulResults(deletionResults._2)

      RoutesAndPrefixesModificationResultPayload(
        message =
          s"Removed successfully ${routesSuccessfullyRemoved.size} routes out of ${deletionResults._1.size} requested " +
            s"and ${groupPrefixesSuccessfullyRemoved.size} prefixes out of ${deletionResults._2.size} requested",
        successfulRoutes = routesSuccessfullyRemoved,
        successfulGroupPrefixes = groupPrefixesSuccessfullyRemoved,
        routesErrors = parseErrors(deletionResults._1),
        groupPrefixesErrors = parseErrors(deletionResults._2)
      )
    }
  }

}
