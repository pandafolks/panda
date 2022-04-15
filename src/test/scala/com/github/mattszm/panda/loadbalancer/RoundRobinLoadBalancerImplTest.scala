package com.github.mattszm.panda.loadbalancer

import com.github.mattszm.panda.participant.{Participant, ParticipantsCache}
import com.github.mattszm.panda.routes.Group
import com.github.mattszm.panda.utils.ClientStub
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.dsl.io.Path
import org.http4s.{Request, Response, Status, Uri}
import org.scalatest.Assertion
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.typelevel.ci.CIString

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

class RoundRobinLoadBalancerImplTest extends AsyncFlatSpec {

  implicit def scheduler: Scheduler = Scheduler.io("monix-task-support-spec")

  private def createLBWithSingleGroup(containAvailable: Boolean = true): LoadBalancer = {
    val client = new ClientStub()
    val tempParticipants = Vector(
      (Participant("59.145.84.51", 4001, Group("cars")), false),     // 0
      (Participant("201.240.55.24", 4001, Group("cars")), false),    // 1
      (Participant("211.188.80.67", 4001, Group("cars")), false),    // 2
      (Participant("13.204.158.90", 3000, Group("cars")), true),     // 3 - first available
      (Participant("44.233.130.109", 4001, Group("cars")), false),   // 4
      (Participant("193.207.130.133", 3000, Group("cars")), true),   // 5 - second available
      (Participant("218.214.92.75", 4002, Group("cars")), false)     // 6
    ).filter(p => containAvailable || !p._2).map(_._1)

    val participantsCache: ParticipantsCache = (_: Group) => tempParticipants // enforcing the participants order
    new RoundRobinLoadBalancerImpl(client, participantsCache)
  }

  "RoundRobinLoadBalancerImpl#route" should "routes to the available server with respect to round-robin algorithm" in {
    val loadBalancer = createLBWithSingleGroup()

    val routeAction = () => loadBalancer.route(
      createRequest("/gateway/cars/rent"),
      Path.unsafeFromString("/api/v1/cars/rent"),
      Group("cars")
    )

    // This test case needs to be highly synchronized
    (0 to 2).foreach { _ =>
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 0) // round robin starts from 0 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 0) // round robin starts from 1 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 0) // round robin starts from 2 and retries until it hits 'first available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 0) // round robin starts from 3 and immediately hits 'first available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 1) // round robin starts from 4 and retries until it hits 'second available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 1) // round robin starts from 5 and immediately hits 'second available'
      fromResponseAssert(Await.result(routeAction().runToFuture, 5.second), 0) // round robin starts from 6 and retries until it hits 'first available'
    }
    succeed
  }

  it should "return `Not Found` if there is no available instance for the requested path" in {
    val loadBalancer = createLBWithSingleGroup()

    loadBalancer.route(
      createRequest("/gateway/planes/passengers"),
      Path.unsafeFromString("rest/api/v1/planes/passengers"),
      Group("planesGroup")
    ).runToFuture.map(res => res.status should be (Status.NotFound))
  }

  it should "return `Not Found` if all servers are unreachable" in {
    val loadBalancer = createLBWithSingleGroup(false)

    loadBalancer.route(
      createRequest("/gateway/cars/rent"),
      Path.unsafeFromString("/api/v1/cars/rent"),
      Group("cars")
    ).runToFuture.map(res => res.status should be (Status.NotFound))
  }

  private def fromResponseAssert(response: Response[Task], routeIndex: Int): Assertion =
    response.headers.headers.find(p => p.name == CIString("from"))
        .fold(fail())(header => header.value should be (ClientStub.availableRoutes.toArray.apply(routeIndex)))

  private def createRequest(path: String): Request[Task] = Request[Task](uri = new Uri(path = Path.unsafeFromString(path)))

}
