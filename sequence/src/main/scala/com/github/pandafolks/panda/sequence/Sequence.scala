package com.github.pandafolks.panda.sequence

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.BsonInt64
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Sequence(key: SequenceKey, seq: BsonInt64)

object Sequence {
  final val SEQUENCE_COLLECTION_NAME = "sequence_generator"

  final val KEY_COLLECTION_NAME = "key"
  final val SEQ_COLLECTION_NAME = "seq"

  def getCollection(dbName: String, collectionName: String = SEQUENCE_COLLECTION_NAME): CollectionCodecRef[Sequence] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[Sequence],
      fromRegistries(
        fromProviders(
          classOf[Sequence],
          classOf[SequenceKey]
        ),
        DEFAULT_CODEC_REGISTRY
      )
    )
}
