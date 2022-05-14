package com.github.mattszm.panda.user.token

import cats.effect.Resource
import com.github.mattszm.panda.configuration.sub.TokensConfig
import com.github.mattszm.panda.participant.event.ParticipantEvent
import com.github.mattszm.panda.sequence.Sequence
import com.github.mattszm.panda.user.{User, UserId}
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.reactormonk.{CryptoBits, PrivateKey}

import java.time.Instant
import java.util.UUID
import scala.io.Codec
import scala.util.Random

final class TokenServiceImpl(private val config: TokensConfig, val c: Resource[Task, (CollectionOperator[User],
  CollectionOperator[ParticipantEvent], CollectionOperator[Sequence], CollectionOperator[Token])]) extends TokenService {
  private final val key = PrivateKey(Codec.toUTF8(Random.alphanumeric.take(20).mkString(""))) //todo: in future it should be static across all instances
  private final val crypto = CryptoBits(key)
  private final val clock = java.time.Clock.systemUTC
  private final val tokenTimeToLive = config.timeToLive


  def signToken(user: User): Task[String] =
    c.use {
      case (_, _, _, tokenOperator) =>
        for {
          tempId <- Task.now(UUID.randomUUID().toString)
          creationTimeStamp = clock.instant().toEpochMilli
          _ <- tokenOperator.single.insertOne(Token(tempId, user._id, creationTimeStamp))
          signedToken <- Task.eval(crypto.signToken(tempId, creationTimeStamp.toString))
        } yield signedToken
    }

  def validateSignedToken(token: String): Task[Option[UserId]] =
    c.use {
      case (_, _, _, tokenOperator) =>
        for {
          validatedSignedToken <- Task.eval(crypto.validateSignedToken(token))
          tokenEntity <-
            if (validatedSignedToken.isDefined)
              tokenOperator.source
                .find(Filters.eq("tempId", validatedSignedToken.get))
                .toListL
                .map(_.sortBy(t => -t.creationTimeStamp)) // very little collision chance
                .map(_.headOption)
            else Task.now(None)
          tokenEntityWithExpiryCheck <- Task.eval(tokenEntity.filter(t =>
            Instant.ofEpochMilli(t.creationTimeStamp).plusSeconds(tokenTimeToLive).isAfter(clock.instant())))
        } yield tokenEntityWithExpiryCheck.map(_.userId)

    }
}
