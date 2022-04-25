package com.github.mattszm.panda.participant

import com.github.mattszm.panda.routes.Group
import io.circe.generic.codec.DerivedAsObjectCodec.deriveCodec
import monix.eval.Task
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.{EntityDecoder, EntityEncoder}


final case class Participant(host: String, port: Int, group: Group, identifier: String) //todo: think how to remove duplicates

object Participant {
  def apply(host: String, port: Int, group: Group): Participant =
    new Participant(host, port, group, List(host, port, group.name).mkString("-"))

  implicit val participantDecoder: EntityDecoder[Task, Participant] = jsonOf[Task, Participant]
  implicit val participantSeqDecoder: EntityDecoder[Task, Seq[Participant]] = jsonOf[Task, Seq[Participant]]

  implicit val participantEncoder: EntityEncoder[Task, Participant] = jsonEncoderOf[Task, Participant]
  implicit val participantSeqEncoder: EntityEncoder[Task, Seq[Participant]] = jsonEncoderOf[Task, Seq[Participant]]
}
