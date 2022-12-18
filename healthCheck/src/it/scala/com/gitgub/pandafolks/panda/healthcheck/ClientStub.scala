package com.gitgub.pandafolks.panda.healthcheck

import cats.data.Kleisli
import cats.effect.Resource
import com.github.pandafolks.panda.utils.EscapeUtils
import monix.eval.Task
import org.http4s.client.Client
import org.http4s._

final class ClientStub extends Client[Task] {
  override def run(req: Request[Task]): Resource[Task, Response[Task]] =
    Resource.eval(
      Task.eval(
        req.uri.toString.dropWhile(_ == EscapeUtils.PATH_SEPARATOR) match {
          case path if ClientStub.AVAILABLE_WORKING_ROUTES.contains(path) =>
            Response[Task](Status.Ok)
          case _ => Response[Task](Status.ServiceUnavailable)
        }
      )
    )

  override def fetch[A](req: Request[Task])(f: Response[Task] => Task[A]): Task[A] = ???

  override def fetch[A](req: Task[Request[Task]])(f: Response[Task] => Task[A]): Task[A] = ???

  override def toKleisli[A](f: Response[Task] => Task[A]): Kleisli[Task, Request[Task], A] = ???

  override def toHttpApp: HttpApp[Task] = ???

  override def stream(req: Request[Task]): fs2.Stream[Task, Response[Task]] = ???

  override def streaming[A](req: Request[Task])(f: Response[Task] => fs2.Stream[Task, A]): fs2.Stream[Task, A] = ???

  override def streaming[A](req: Task[Request[Task]])(f: Response[Task] => fs2.Stream[Task, A]): fs2.Stream[Task, A] =
    ???

  override def expectOr[A](req: Request[Task])(onError: Response[Task] => Task[Throwable])(implicit
      d: EntityDecoder[Task, A]
  ): Task[A] = ???

  override def expect[A](req: Request[Task])(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def expectOr[A](req: Task[Request[Task]])(onError: Response[Task] => Task[Throwable])(implicit
      d: EntityDecoder[Task, A]
  ): Task[A] = ???

  override def expect[A](req: Task[Request[Task]])(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def expectOr[A](uri: Uri)(onError: Response[Task] => Task[Throwable])(implicit
      d: EntityDecoder[Task, A]
  ): Task[A] = ???

  override def expect[A](uri: Uri)(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def expectOr[A](s: String)(onError: Response[Task] => Task[Throwable])(implicit
      d: EntityDecoder[Task, A]
  ): Task[A] = ???

  override def expect[A](s: String)(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def expectOptionOr[A](req: Request[Task])(onError: Response[Task] => Task[Throwable])(implicit
      d: EntityDecoder[Task, A]
  ): Task[Option[A]] = ???

  override def expectOption[A](req: Request[Task])(implicit d: EntityDecoder[Task, A]): Task[Option[A]] = ???

  override def fetchAs[A](req: Request[Task])(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def fetchAs[A](req: Task[Request[Task]])(implicit d: EntityDecoder[Task, A]): Task[A] = ???

  override def status(req: Request[Task]): Task[Status] = ???

  override def status(req: Task[Request[Task]]): Task[Status] = ???

  override def statusFromUri(uri: Uri): Task[Status] = ???

  override def statusFromString(s: String): Task[Status] = ???

  override def successful(req: Request[Task]): Task[Boolean] = ???

  override def successful(req: Task[Request[Task]]): Task[Boolean] = ???

  override def get[A](uri: Uri)(f: Response[Task] => Task[A]): Task[A] = ???

  override def get[A](s: String)(f: Response[Task] => Task[A]): Task[A] = ???
}

object ClientStub {
  final val AVAILABLE_WORKING_ROUTES: List[String] = List(
    "13.204.158.92:3000/api/v1/health",
    "193.207.130.139:3005/healthcheck"
  )
}
