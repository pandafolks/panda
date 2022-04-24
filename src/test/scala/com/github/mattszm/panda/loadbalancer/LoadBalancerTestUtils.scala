package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.{ClientStub, PersistenceError}
import monix.eval.Task
import org.http4s.dsl.io.Path
import org.http4s.{Request, Response, Uri}
import org.scalatest.Assertion
import org.scalatest.Assertions.fail
import org.scalatest.matchers.must.Matchers.contain
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.typelevel.ci.CIString

object LoadBalancerTestUtils {
  def createParticipantsCacheWithSingleGroup(containAvailable: Boolean = true): ParticipantsCache = {
    val tempParticipants = Vector(
      (Participant("59.145.84.51", 4001, Group("cars")), false),     // 0
      (Participant("13.204.158.90", 4001, Group("cars")), false),    // 1 - looks like the first available but with wrong port
      (Participant("211.188.80.67", 4001, Group("cars")), false),    // 2
      (Participant("13.204.158.90", 3000, Group("cars")), true),     // 3 - first available
      (Participant("44.233.130.109", 4001, Group("cars")), false),   // 4
      (Participant("193.207.130.133", 3000, Group("cars")), true),   // 5 - second available
      (Participant("218.214.92.75", 4002, Group("cars")), false)     // 6
    ).filter(p => containAvailable || !p._2).map(_._1)

    new ParticipantsCache {
      override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] = Task.now { tempParticipants } // enforcing the participants order

      override def addParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] = ???

      override def addParticipants(participants: List[Participant]): Task[Either[PersistenceError, Unit]] = ???

      override def removeParticipant(participant: Participant): Task[Either[PersistenceError, Unit]] = ???

      override def removeAllParticipantsAssociatedWithGroup(group: Group): Task[Either[PersistenceError, Unit]] = ???
    }
  }

  def createRequest(path: String): Request[Task] =
    Request[Task](uri = new Uri(path = Path.unsafeFromString(path)))

  def commonRouteAction(loadBalancer: LoadBalancer): Task[Response[Task]] =
    loadBalancer.route(
      LoadBalancerTestUtils.createRequest("/gateway/cars/rent"),
      Path.unsafeFromString("/api/v1/cars/rent"),
      Group("cars")
    )

  def fromResponseAssert(response: Response[Task]): Assertion =
    response.headers.headers.find(p => p.name == CIString("from"))
      .fold(fail())(header => ClientStub.availableRoutes should contain (header.value))
}
