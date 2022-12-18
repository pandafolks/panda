package com.github.pandafolks.panda.user.token

import cats.data.OptionT
import cats.effect.Resource
import com.github.pandafolks.panda.user.{User, UserId}
import com.github.pandafolks.panda.utils.SystemProperties
import com.github.pandafolks.panda.utils.cache.{CustomCache, CustomCacheImpl}
import com.google.common.annotations.VisibleForTesting
import com.mongodb.client.model.Filters
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task
import org.reactormonk.{CryptoBits, PrivateKey}

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.DurationInt
import scala.io.{Codec, Source}
import scala.util.{Random, Try}

final class TokenServiceImpl(private val config: TokensConfig)(
    private val c: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]
) extends TokenService {

  private final val key = PrivateKey(
    Codec.toUTF8(
      Option(SystemProperties.usersTokenKey)
        .orElse(Try {
          Source.fromResource("tokenKey.txt").getLines().mkString
        }.toOption)
        .getOrElse(Random.alphanumeric.take(20).mkString(""))
    )
  )

  @VisibleForTesting private final val crypto = CryptoBits(key)

  private final val clock = java.time.Clock.systemUTC
  private final val tokenTimeToLive = config.timeToLive

  // purpose: better handling of the batched requests (maintenance scripts, etc)
  private val cache: CustomCache[String, Option[Token]] = new CustomCacheImpl[String, Option[Token]](tempId =>
    c.use { case (_, tokenOperator) =>
      tokenOperator.source
        .find(Filters.eq(Token.TEMP_ID_COLLECTION_NAME, tempId))
        .toListL
        .map(_.sortBy(t => -t.creationTimeStamp)) // very little collision chance so sorting in memory is ok
        .map(_.headOption)
    }
  )(maximumSize = 50L, ttl = 60.seconds)

  def signToken(user: User): Task[String] =
    c.use { case (_, tokenOperator) =>
      for {
        tempId <- Task.now(UUID.randomUUID().toString)
        creationTimeStamp = clock.instant().toEpochMilli
        _ <- tokenOperator.single.insertOne(Token(tempId, user.id, creationTimeStamp))
        signedToken <- Task.eval(crypto.signToken(tempId, creationTimeStamp.toString))
      } yield signedToken
    }

  def validateSignedToken(token: String): Task[Option[UserId]] =
    for {
      tokenEntity <- OptionT(Task.eval(crypto.validateSignedToken(token)))
        .flatMap(validatedSignedToken => OptionT(cache.get(validatedSignedToken)))
        .value
      tokenEntityWithExpiryCheck <- Task.eval(
        tokenEntity.filter(t => Instant.ofEpochMilli(t.creationTimeStamp).plusSeconds(tokenTimeToLive).isAfter(clock.instant()))
      )
    } yield tokenEntityWithExpiryCheck.map(_.userId)

}
