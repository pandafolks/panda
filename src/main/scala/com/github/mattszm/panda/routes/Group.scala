package com.github.mattszm.panda.routes

import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import monix.eval.Task
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf

final case class Group(name: String)

object Group {
  implicit val groupDecoder: EntityDecoder[Task, Group] = jsonOf[Task, Group]
}
