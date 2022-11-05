package com.pandafolks.mattszm.panda.sequence

import com.github.pandafolks.panda.utils.PersistenceError
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.mongodb.scala.bson.BsonInt64

trait SequenceDao {

  /** Generate and return the next sequence value for a provided key.
    *
    * @param key
    *   against which the sequence value will be generated
    * @param seqOperator
    *   [[Sequence]] DB entry point
    * @return
    *   Either generated sequence value or PersistenceError if the error during sequence creation occurred
    */
  def getNextSequence(
      key: SequenceKey,
      seqOperator: CollectionOperator[Sequence]
  ): Task[Either[PersistenceError, BsonInt64]]
}
