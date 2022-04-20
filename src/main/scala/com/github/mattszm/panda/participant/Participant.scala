package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import monix.eval.Task
import org.http4s.EntityDecoder
import org.http4s.circe.jsonOf


final case class Participant(host: String, port: Int, group: Group, identifier: String = "")

object Participant {
  implicit val participantDecoder: EntityDecoder[Task, Participant] = jsonOf[Task, Participant]
  implicit val participantSeqDecoder: EntityDecoder[Task, Seq[Participant]] = jsonOf[Task, Seq[Participant]]
}

