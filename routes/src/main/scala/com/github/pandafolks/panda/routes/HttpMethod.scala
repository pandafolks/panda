package com.github.pandafolks.panda.routes

sealed trait HttpMethod {
  def getName: String
}

object HttpMethod {

  case object Get extends HttpMethod {
    override def getName: String = "GET"
  }

  case object Post extends HttpMethod {
    override def getName: String = "POST"
  }

  case object Put extends HttpMethod {
    override def getName: String = "PUT"
  }

  case object Patch extends HttpMethod {
    override def getName: String = "PATCH"
  }

  case object Delete extends HttpMethod {
    override def getName: String = "DELETE"
  }

  val values: List[HttpMethod] = List(Get, Post, Put, Patch, Delete)

  val valuesByName: Map[String, HttpMethod] =
    values.foldLeft(Map.empty[String, HttpMethod])((prevState, header) => prevState + (header.getName -> header))

  def getByName(name: String): HttpMethod = valuesByName.getOrElse(name.toUpperCase(), Get)

  def getByName(name: Option[String]): HttpMethod = name.flatMap(n => valuesByName.get(n.toUpperCase())).getOrElse(Get)

  def unify(name: Option[String]): String = getByName(name).getName

}
