package com.github.mattszm.panda.sequence

import org.mongodb.scala.bson.BsonInt32

final case class Sequence(key: SequenceKey, seq: BsonInt32)
