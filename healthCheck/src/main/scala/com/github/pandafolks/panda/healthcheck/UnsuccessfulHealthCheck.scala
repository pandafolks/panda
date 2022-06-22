package com.github.pandafolks.panda.healthcheck

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class UnsuccessfulHealthCheck(identifier: String, counter: Long)

object UnsuccessfulHealthCheck {
  final val UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME = "unsuccessful_health_check"

  def getCollection(dbName: String, collectionName: String = UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME): CollectionCodecRef[UnsuccessfulHealthCheck] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[UnsuccessfulHealthCheck],
      fromRegistries(fromProviders(
        classOf[UnsuccessfulHealthCheck],
      ), DEFAULT_CODEC_REGISTRY)
    )
}
