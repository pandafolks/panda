package com.github.pandafolks.panda.loadbalancer

import com.github.pandafolks.panda.participant.{Participant, ParticipantsCache, ParticipantsCacheImpl}
import com.github.pandafolks.panda.participant.event.ParticipantEventService
import com.github.pandafolks.panda.routes.Group
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.Scheduler.global
import org.http4s.Status
import org.http4s.dsl.io.Path
import org.mockito.Mockito.mock
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class HashLoadBalancerImplTest extends AsyncFlatSpec with ScalaFutures {
  implicit final val scheduler: Scheduler = global

  private val mockParticipantEventService = mock(classOf[ParticipantEventService])

  private def createRandomLBWithSingleGroup(containAvailable: Boolean = true, containUnavailable: Boolean = false): LoadBalancer = {
    new HashLoadBalancerImpl(
      new ClientStub(),
      LoadBalancerTestUtils.createParticipantsCacheWithSingleGroup(containAvailable, containUnavailable),
      new ConsistentHashingState()
    )
  }

  "RandomLoadBalancerImpl#route" should "route to the available server" in {
    val loadBalancer = createRandomLBWithSingleGroup()

    val f = Task.traverse((0 to 20).toList)(_ => LoadBalancerTestUtils.commonRouteAction(loadBalancer)).runToFuture
    whenReady(f, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(LoadBalancerTestUtils.fromResponseAssert)
        succeed
    }
  }

  it should "route to the available server (multi-thread environment)" in {
    val loadBalancer = createRandomLBWithSingleGroup()

    val f = Task.parTraverseN(8)((0 to 40).toList)(_ => LoadBalancerTestUtils.commonRouteAction(loadBalancer)).runToFuture
    whenReady(f, Timeout.apply(Span.apply(10, Seconds))) {
      res =>
        res.map(LoadBalancerTestUtils.fromResponseAssert)
        succeed
    }
  }

  it should "route all requests from same client to the same server" in {
    val loadBalancer = createRandomLBWithSingleGroup()

    val f = Task.parTraverseN(8)((0 to 40).toList)(_ => LoadBalancerTestUtils.commonRouteAction(loadBalancer)).runToFuture
    whenReady(f, Timeout.apply(Span.apply(10, Seconds))) {
      responsesList =>
        var server: Option[String] = Option.empty
        responsesList.foreach(singleResponse => {
          val v = LoadBalancerTestUtils.fromResponseAssertAndReturnFrom(singleResponse)
          if (server.isEmpty) {
            server = Some(v)
          } else if (v != server.get) { fail() }
        })
        succeed
    }
  }

  it should "return `Not Found` if there is no available instance for the requested path" in {
    val client = new ClientStub()
    val tempParticipants = List(
      Participant("59.145.84.51", 4001, Group("cars")),
      Participant("193.207.130.133", 3000, Group("cars")),
      Participant("218.214.92.75", 4002, Group("cars"))
    )
    val participantsCache: ParticipantsCache = Await.result(ParticipantsCacheImpl(
      mockParticipantEventService, tempParticipants).runToFuture, 5.seconds)
    val loadBalancer: LoadBalancer = new HashLoadBalancerImpl(
      client,
      participantsCache,
      new ConsistentHashingState()
    )

    loadBalancer.route(
      LoadBalancerTestUtils.createRequest("/gateway/planes/passengers"),
      Path.unsafeFromString("rest/api/v1/planes/passengers"),
      Group("planesGroup")
    ).runToFuture.map(_.status should be(Status.NotFound))
  }

  it should "return `Not Found` if all servers are unreachable" in {
    val loadBalancer = createRandomLBWithSingleGroup(containAvailable = false, containUnavailable = true)

    LoadBalancerTestUtils.commonRouteAction(loadBalancer).runToFuture
      .map(_.status should be (Status.NotFound))
  }
}
