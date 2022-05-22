package com.github.mattszm.panda.user.token

import com.github.mattszm.panda.user.UserId
import monix.connect.mongodb.client.CollectionCodecRef
import org.bson.UuidRepresentation
import org.bson.codecs.UuidCodec
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
import org.mongodb.scala.bson.codecs.Macros.createCodecProvider

final case class Token(tempId: String, userId: UserId, creationTimeStamp: Long)

object Token {
  final val TOKENS_COLLECTION_NAME = "tokens"

  private val javaCodecs = CodecRegistries.fromCodecs(
    new UuidCodec(UuidRepresentation.STANDARD)
  )

  def getCollection(dbName: String): CollectionCodecRef[Token] = CollectionCodecRef(
    dbName,
    TOKENS_COLLECTION_NAME,
    classOf[Token],
    fromRegistries(fromProviders(classOf[Token]), javaCodecs)
  )
}
