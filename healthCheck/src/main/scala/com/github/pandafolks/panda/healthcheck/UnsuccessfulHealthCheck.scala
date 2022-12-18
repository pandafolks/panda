package com.github.pandafolks.panda.healthcheck

import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class UnsuccessfulHealthCheck(
    identifier: String,
    counter: Long,
    lastUpdateTimestamp: Long,
    turnedOff: Boolean
)

object UnsuccessfulHealthCheck {
  final val UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME = "unsuccessful_health_check"

  final val IDENTIFIER_PROPERTY_NAME = "identifier"
  final val COUNTER_PROPERTY_NAME = "counter"
  final val LAST_UPDATE_TIMESTAMP_PROPERTY_NAME = "lastUpdateTimestamp"
  final val TURNED_OFF_PROPERTY_NAME = "turnedOff"

  def getCollection(
      dbName: String,
      collectionName: String = UNSUCCESSFUL_HEALTH_CHECK_COLLECTION_NAME
  ): CollectionCodecRef[UnsuccessfulHealthCheck] =
    CollectionCodecRef(
      dbName,
      collectionName,
      classOf[UnsuccessfulHealthCheck],
      fromRegistries(
        fromProviders(
          classOf[UnsuccessfulHealthCheck]
        ),
        DEFAULT_CODEC_REGISTRY
      )
    )
}
