package com.github.pandafolks.panda.user.token

import com.github.pandafolks.panda.user.{User, UserTokenFixture, tagUUIDAsUserId}
import monix.eval.Task
import monix.execution.Scheduler
import org.reactormonk.CryptoBits
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, EitherValues, PrivateMethodTester}
import tsec.passwordhashers.jca.BCrypt

import java.util.UUID
import scala.concurrent.duration.DurationInt

class TokenServiceItTest extends AsyncFlatSpec with UserTokenFixture with Matchers with ScalaFutures
  with EitherValues with BeforeAndAfterAll with PrivateMethodTester{
  implicit val scheduler: Scheduler = Scheduler.io("user-service-it-test")

  implicit val defaultConfig: PatienceConfig = PatienceConfig(30.seconds, 100.milliseconds)

  override protected def afterAll(): Unit = mongoContainer.stop()

  "signToken and validateSignedToken" should "cooperate and handle successful case" in {
    val userId = tagUUIDAsUserId(UUID.randomUUID())
    val username = randomString("username")
    val password = randomString("password")
    val userT = for {
      pwd <- BCrypt.hashpw[Task](password)
    } yield User(userId, username, pwd)

    val validateSignedTokenF = userT
      .flatMap(u => tokenService.signToken(u))
      .flatMap(token => tokenService.validateSignedToken(token))
      .runToFuture

    whenReady(validateSignedTokenF) { res => {
      res.isDefined should be(true)
      res.get should be(userId)
    }}
  }

  it should "cooperate and handle unsuccessful case (token not valid)" in {
    val userId = tagUUIDAsUserId(UUID.randomUUID())
    val username = randomString("username")
    val password = randomString("password")
    val userT = for {
      pwd <- BCrypt.hashpw[Task](password)
    } yield User(userId, username, pwd)

    val validateSignedTokenF = userT
      .flatMap(u => tokenService.signToken(u))
      .flatMap(_ => tokenService.validateSignedToken("blabla"))
      .runToFuture

    whenReady(validateSignedTokenF) { res => {
      res.isDefined should be(false)
    }}
  }

  it should "cooperate and handle unsuccessful case (token not present in db)" in {
    val crypto = PrivateMethod[CryptoBits](Symbol("crypto"))
    val tempId = UUID.randomUUID().toString
    val clock = java.time.Clock.systemUTC
    val manuallyCreatedValidToken = tokenService.invokePrivate(crypto())
      .signToken(tempId, clock.instant().toEpochMilli.toString)

    val validateSignedTokenF = tokenService.validateSignedToken(manuallyCreatedValidToken).runToFuture

    whenReady(validateSignedTokenF) { res => {
      res.isDefined should be(false)
    }}
  }

  it should "cooperate and handle unsuccessful case (token expired)" in {
    val userId = tagUUIDAsUserId(UUID.randomUUID())
    val username = randomString("username")
    val password = randomString("password")
    val userT = for {
      pwd <- BCrypt.hashpw[Task](password)
    } yield User(userId, username, pwd)

    val validateSignedTokenF = userT
      .flatMap(u => tokenService.signToken(u))
      .flatMap(token => Task.sleep(4.seconds).map(_ => token)) // tokens ttl is set to 3 seconds
      .flatMap(token => tokenService.validateSignedToken(token))
      .runToFuture

    whenReady(validateSignedTokenF) { res => {
      res.isDefined should be(false)
    }}
  }
}
