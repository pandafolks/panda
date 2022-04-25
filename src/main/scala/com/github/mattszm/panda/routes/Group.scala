package com.github.mattszm.panda.routes

import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import monix.eval.Task
import org.http4s.{EntityDecoder, EntityEncoder}
import org.http4s.circe.{jsonEncoderOf, jsonOf}

final case class Group(name: String)

object Group {
  implicit val groupDecoder: EntityDecoder[Task, Group] = jsonOf[Task, Group]

  implicit val groupEncoder: EntityEncoder[Task, Group] = jsonEncoderOf[Task, Group]
  implicit val groupSeqEncoder: EntityEncoder[Task, Seq[Group]] = jsonEncoderOf[Task, Seq[Group]]
}
