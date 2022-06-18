package com.github.pandafolks.panda.user

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}

final case class UserCredentials(username: String, password: String)

object UserCredentials {
  implicit val loginUserEncoder: Encoder[UserCredentials] = deriveEncoder
  implicit val loginUserDecoder: Decoder[UserCredentials] = deriveDecoder

  implicit val entityLoginUserEncoder: EntityEncoder[Task, UserCredentials] = jsonEncoderOf[Task, UserCredentials]
  implicit val entityLoginUserDecoder: EntityDecoder[Task, UserCredentials] = jsonOf[Task, UserCredentials]
}
