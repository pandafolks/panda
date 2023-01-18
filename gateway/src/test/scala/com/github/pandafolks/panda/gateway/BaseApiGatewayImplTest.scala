package com.github.pandafolks.panda.gateway

import com.github.pandafolks.panda.loadbalancer.LoadBalancer
import com.github.pandafolks.panda.routes.RoutesTree.RouteInfo
import com.github.pandafolks.panda.routes.entity.MappingContent
import com.github.pandafolks.panda.routes.{Group, TreesService}
import com.github.pandafolks.panda.utils.scheduler.CoreScheduler
import monix.eval.Task
import monix.execution.Scheduler
import org.http4s.Uri.Path
import org.http4s.{Method, Request, Response, Status}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.mockito.{AdditionalMatchers, ArgumentMatchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AsyncFlatSpec
import org.scalatest.matchers.must.Matchers.be
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

class BaseApiGatewayImplTest extends AsyncFlatSpec with ScalaFutures {
  implicit final val scheduler: Scheduler = CoreScheduler.scheduler

  "ask" should "work with happy path" in {
    val loadBalancer = mock(classOf[LoadBalancer])
    val treesService = mock(classOf[TreesService])

    val underTest: ApiGateway = new BaseApiGatewayImpl(loadBalancer = loadBalancer, treesService = treesService)

    when(treesService.findStandaloneRoute(any[Path], any[Method])) thenReturn Task.now(
      Some(
        (
          RouteInfo(
            mappingContent = MappingContent(
              left = Some("cars"),
              right = Option.empty
            ),
            isPocket = false,
            isStandalone = true
          ),
          Map("key" -> "value")
        )
      )
    )

    when(treesService.findPrefix(any[Group])) thenReturn Task.now(Path.unsafeFromString("someprefix"))

    when(loadBalancer.route(any[Request[Task]](), ArgumentMatchers.eq(Path.unsafeFromString("someprefix/whatever")), any[Group]()))
      .thenReturn(Task.now(Response.apply(Status.Ok)))
    when(
      loadBalancer.route(
        any[Request[Task]](),
        AdditionalMatchers.not(ArgumentMatchers.eq(Path.unsafeFromString("someprefix/whatever"))),
        any[Group]()
      )
    ).thenReturn(Task.now(Response.apply(Status.BadRequest)))

    underTest.ask(Request.apply[Task](), Path.unsafeFromString("whatever")).runToFuture.futureValue.status should be(Status.Ok)
  }

  "ask" should "be able to return not-successful response" in {
    val loadBalancer = mock(classOf[LoadBalancer])
    val treesService = mock(classOf[TreesService])

    val underTest: ApiGateway = new BaseApiGatewayImpl(loadBalancer = loadBalancer, treesService = treesService)

    when(treesService.findStandaloneRoute(any[Path], any[Method])) thenReturn Task.now(
      Some(
        (
          RouteInfo(
            mappingContent = MappingContent(
              left = Some("cars"),
              right = Option.empty
            ),
            isPocket = false,
            isStandalone = true
          ),
          Map("key" -> "value")
        )
      )
    )

    when(treesService.findPrefix(any[Group])) thenReturn Task.now(Path.unsafeFromString("someprefix"))

    when(loadBalancer.route(any[Request[Task]](), ArgumentMatchers.eq(Path.unsafeFromString("someprefix/whatever")), any[Group]()))
      .thenReturn(Task.now(Response.apply(Status.Unauthorized)))
    when(
      loadBalancer.route(
        any[Request[Task]](),
        AdditionalMatchers.not(ArgumentMatchers.eq(Path.unsafeFromString("someprefix/whatever"))),
        any[Group]()
      )
    ).thenReturn(Task.now(Response.apply(Status.BadRequest)))

    underTest.ask(Request.apply[Task](), Path.unsafeFromString("whatever")).runToFuture.futureValue.status should be(Status.Unauthorized)
  }

  it should "not handle composition routes" in {
    val loadBalancer = mock(classOf[LoadBalancer])
    val treesService = mock(classOf[TreesService])

    val underTest: ApiGateway = new BaseApiGatewayImpl(loadBalancer = loadBalancer, treesService = treesService)

    when(treesService.findStandaloneRoute(any[Path], any[Method])) thenReturn Task.now(
      Some(
        (
          RouteInfo(
            mappingContent = MappingContent(
              left = Option.empty,
              right = Some(Map.empty) // whatever...
            ),
            isPocket = false,
            isStandalone = true
          ),
          Map("key" -> "value")
        )
      )
    )

    underTest.ask(Request.apply[Task](), Path.unsafeFromString("whatever")).runToFuture.futureValue.status should be(Status.BadRequest)
  }

  it should "fail fast if the route is not found" in {
    val loadBalancer = mock(classOf[LoadBalancer])
    val treesService = mock(classOf[TreesService])

    val underTest: ApiGateway = new BaseApiGatewayImpl(loadBalancer = loadBalancer, treesService = treesService)

    when(treesService.findStandaloneRoute(any[Path], any[Method])) thenReturn Task.now(Option.empty)

    underTest.ask(Request.apply[Task](), Path.unsafeFromString("whatever")).runToFuture.futureValue.status should be(Status.NotFound)
  }
}
