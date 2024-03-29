package com.github.pandafolks.panda.loadbalancer

import com.comcast.ip4s.SocketAddress
import com.github.pandafolks.panda.participant.{Healthy, Participant, ParticipantsCache}
import com.github.pandafolks.panda.routes.Group
import com.github.pandafolks.panda.utils.listener.ChangeListener
import monix.eval.Task
import org.http4s.Request.Keys
import org.http4s.dsl.io.Path
import org.http4s.{Request, Response, Uri}
import org.scalatest.Assertion
import org.scalatest.Assertions.fail
import org.scalatest.matchers.must.Matchers.contain
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.typelevel.ci.CIString
import org.typelevel.vault.Vault

object LoadBalancerTestUtils {
  def createParticipantsCacheWithSingleGroup(
      containAvailable: Boolean = true,
      containUnavailable: Boolean = true
  ): ParticipantsCache = {
    val tempParticipants = Vector(
      (Participant("59.145.84.51", 4001, Group("cars")), false), // 0
      (
        Participant("13.204.158.90", 4001, Group("cars")),
        false
      ), // 1 - looks like the first available but with wrong port
      (Participant("211.188.80.67", 4001, Group("cars")), false), // 2
      (Participant("13.204.158.90", 3000, Group("cars")), true), // 3 - first available
      (Participant("44.233.130.109", 4001, Group("cars")), false), // 4
      (Participant("193.207.130.133", 3000, Group("cars")), true), // 5 - second available
      (Participant("218.214.92.75", 4002, Group("cars")), false) // 6
    ).filter(p => containAvailable || !p._2)
      .filter(p => containUnavailable || p._2)
      .map(_._1)

    new ParticipantsCache {

      override def getAllGroups: Task[List[Group]] = ???

      override def getAllParticipants: Task[List[Participant]] = ???

      override def getAllWorkingParticipants: Task[List[Participant]] = ???

      override def getAllHealthyParticipants: Task[List[Participant]] = ???

      override def getParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] = ???

      override def getWorkingParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] = ???

      override def getHealthyParticipantsAssociatedWithGroup(group: Group): Task[Vector[Participant]] = Task.now {
        tempParticipants
      } // enforcing the participants order

      override def registerListener(listener: ChangeListener[Participant]): Task[Unit] = listener.notifyAboutAdd(
        tempParticipants.map(_.copy(health = Healthy))
      ) // enforcing all participants to be healthy
    }
  }

  def createRequest(path: String): Request[Task] =
    Request[Task](
      uri = new Uri(path = Path.unsafeFromString(path)),
      attributes = Vault.insert(
        Keys.ConnectionInfo,
        Request.Connection(
          SocketAddress.fromStringIp("140.0.168.31:3002").get,
          SocketAddress.fromStringIp("127.178.244.254:9999").get,
          secure = false
        ),
        Vault.empty
      )
    )

  def commonRouteAction(loadBalancer: LoadBalancer): Task[Response[Task]] =
    loadBalancer.route(
      LoadBalancerTestUtils.createRequest("/gateway/cars/rent"),
      Path.unsafeFromString("/api/v1/cars/rent"),
      Group("cars")
    )

  def fromResponseAssert(response: Response[Task]): Assertion =
    response.headers.headers
      .find(p => p.name == CIString("from"))
      .fold(fail())(header => ClientStub.AVAILABLE_ROUTES should contain(header.value))

  def fromResponseAssertAndReturnFrom(response: Response[Task]): String =
    response.headers.headers
      .find(p => p.name == CIString("from"))
      .fold(fail())(header => { ClientStub.AVAILABLE_ROUTES should contain(header.value); header.value })
}
