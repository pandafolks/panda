package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.event.ParticipantEventService
import com.github.mattszm.panda.participant.{Participant, ParticipantsCache, ParticipantsCacheImpl}
import com.github.mattszm.panda.routes.Group
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import org.http4s.dsl.io.Path
import org.http4s.{Response, Status}
import org.mockito.Mockito.mock
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.typelevel.ci.CIString

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RoundRobinLoadBalancerImplTest extends AsyncFlatSpec {
  implicit final val scheduler: Scheduler = global

  private val mockParticipantEventService = mock(classOf[ParticipantEventService])

  private def createRoundRobinLBWithSingleGroup(containAvailable: Boolean = true): LoadBalancer =
    new RoundRobinLoadBalancerImpl(
      new ClientStub(),
      LoadBalancerTestUtils.createParticipantsCacheWithSingleGroup(containAvailable)
    )

  "RoundRobinLoadBalancerImpl#route" should "route to the available server with respect to round-robin algorithm" in {
    val loadBalancer = createRoundRobinLBWithSingleGroup()

    def routeAction: Task[Response[Task]] = LoadBalancerTestUtils.commonRouteAction(loadBalancer)

    // This test case needs to be highly synchronized
    (0 to 3).foreach { _ =>
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 0) // round robin starts from 0 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 0) // round robin starts from 1 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 0) // round robin starts from 2 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 0) // round robin starts from 3 and immediately hits 'first available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 1) // round robin starts from 4 and retries until it hits 'second available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 1) // round robin starts from 5 and immediately hits 'second available'
      fromResponseAssert(Await.result(routeAction.runToFuture, 5.second), 0) // round robin starts from 6 and retries until it hits 'first available'
    }
    succeed
  }

  it should "route to the available server (multi-thread environment)" in {
    val loadBalancer = createRoundRobinLBWithSingleGroup()

    Await.result(
      Task.parTraverseN(8)((0 to 40).toList)
      (_ => LoadBalancerTestUtils.commonRouteAction(loadBalancer)).runToFuture,
      30.seconds
    ).map(LoadBalancerTestUtils.fromResponseAssert)
    succeed
  }

    it should "return `Not Found` if there is no available instance for the requested path" in {
    val client = new ClientStub()
    val tempParticipants = List(
      Participant("59.145.84.51", 4001, Group("cars")),
      Participant("193.207.130.133", 3000, Group("cars")),
      Participant("218.214.92.75", 4002, Group("cars"))
    )
    val participantsCache: ParticipantsCache = Await.result(
      ParticipantsCacheImpl(mockParticipantEventService, tempParticipants).runToFuture, 5.seconds)
    val loadBalancer: LoadBalancer = new RoundRobinLoadBalancerImpl(client, participantsCache)

    loadBalancer.route(
      LoadBalancerTestUtils.createRequest("/gateway/planes/passengers"),
      Path.unsafeFromString("rest/api/v1/planes/passengers"),
      Group("planesGroup")
    ).runToFuture.map(_.status should be (Status.NotFound))
  }

  it should "return `Not Found` if all servers are unreachable" in {
    val loadBalancer = createRoundRobinLBWithSingleGroup(false)

    LoadBalancerTestUtils.commonRouteAction(loadBalancer).runToFuture
      .map(_.status should be (Status.NotFound))
  }

  private def fromResponseAssert(response: Response[Task], availableRouteIndex: Int): Assertion =
    response.headers.headers.find(p => p.name == CIString("from"))
        .fold(fail())(_.value should be (ClientStub.availableRoutes.toArray.apply(availableRouteIndex)))
}
