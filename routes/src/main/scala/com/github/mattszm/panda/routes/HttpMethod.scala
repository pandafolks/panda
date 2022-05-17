package com.github.mattszm.panda.routes

sealed trait HttpMethod

object HttpMethod {
  case object Get extends HttpMethod

  case object Post extends HttpMethod
}
