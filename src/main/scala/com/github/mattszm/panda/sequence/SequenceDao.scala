package com.github.mattszm.panda.sequence

import cats.effect.Resource
import com.github.mattszm.panda.participant.event.ParticipantEvent
import com.github.mattszm.panda.user.User
import com.github.mattszm.panda.user.token.Token
import com.github.mattszm.panda.utils.{PersistenceError, UnsuccessfulUpdateOperation}
import com.mongodb.client.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.bson.BsonInt32

final class SequenceDao(private val c: Resource[Task, (CollectionOperator[User],
  CollectionOperator[ParticipantEvent], CollectionOperator[Sequence], CollectionOperator[Token])]) { // todo: c not used

  def getNextSequence(key: SequenceKey, seqOperator: CollectionOperator[Sequence]): Task[Either[PersistenceError, BsonInt32]] = {
    val filter = Filters.eq("key", key)
    val update = Updates.combine(Updates.inc("seq", 1))
    val options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)
    seqOperator.source.findOneAndUpdate(
      filter = filter,
      update = update,
      findOneAndUpdateOptions = options
    ).map(_.map(_.seq))
      .map(_.toRight(UnsuccessfulUpdateOperation("no update performed")))
  }
}
