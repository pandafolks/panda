package com.pandafolks.mattszm.panda.sequence

import com.github.pandafolks.panda.utils.{PersistenceError, UnsuccessfulUpdateOperation}
import com.mongodb.client.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.bson.BsonInt32

final class SequenceDao {

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
