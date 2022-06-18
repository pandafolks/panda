package com.github.pandafolks.panda.routes

sealed trait HttpMethod

object HttpMethod {
  case object Get extends HttpMethod

  case object Post extends HttpMethod

  def getValues: List[HttpMethod] = List(Get, Post)
}
