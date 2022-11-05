package com.github.pandafolks.panda.utils.http

import cats.Applicative
import cats.syntax.all._
import org.http4s.{EntityEncoder, Response, Status}

object Responses {

  def notFoundWithInfo[F[_]: Applicative](info: String)(implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
    Response[F](Status.NotFound).withEntity(info).pure[F]

  def badRequestWithInfo[F[_]: Applicative](info: String)(implicit encoder: EntityEncoder[F, String]): F[Response[F]] =
    Response[F](Status.BadRequest).withEntity(info).pure[F]

  def serviceUnavailableWithInfo[F[_]: Applicative](info: String)(implicit
      encoder: EntityEncoder[F, String]
  ): F[Response[F]] =
    Response[F](Status.ServiceUnavailable).withEntity(info).pure[F]
}
