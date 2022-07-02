package com.github.pandafolks.panda.routes

sealed trait HttpMethod {
  def getName: String
}

object HttpMethod {

  case object Get extends HttpMethod {
    override def getName: String = "get"
  }

  case object Post extends HttpMethod {
    override def getName: String = "post"
  }

  case object Put extends HttpMethod {
    override def getName: String = "put"
  }

  case object Patch extends HttpMethod {
    override def getName: String = "patch"
  }

  case object Delete extends HttpMethod {
    override def getName: String = "delete"
  }

  val values: List[HttpMethod] = List(Get, Post, Put, Patch, Delete)

  val valuesByName: Map[String, HttpMethod] =
    values.foldLeft(Map.empty[String, HttpMethod])((prevState, header) => prevState + (header.getName -> header))

  def getByName(name: String): Option[HttpMethod] = valuesByName.get(name.toLowerCase())

}
