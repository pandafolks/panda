package com.pandafolks.mattszm.panda.sequence

import com.github.pandafolks.panda.utils.{PersistenceError, UnsuccessfulUpdateOperation}
import com.mongodb.client.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.bson.BsonInt64

final class SequenceDao {

  def getNextSequence(key: SequenceKey, seqOperator: CollectionOperator[Sequence]): Task[Either[PersistenceError, BsonInt64]] = {
    val filter = Filters.eq("key", key)
    val update = Updates.inc("seq", 1L)
    val options = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)
    seqOperator.source.findOneAndUpdate(
      filter = filter,
      update = update,
      findOneAndUpdateOptions = options
    ).map(_.map(_.seq))
      .map(_.toRight(UnsuccessfulUpdateOperation("No update performed")))
  }
}
