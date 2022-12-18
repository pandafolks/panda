package com.github.pandafolks.panda.routes

sealed trait HttpMethod {
  def getName: String
}

object HttpMethod {

  case class Get() extends HttpMethod {
    override def getName: String = "GET"
  }

  case class Post() extends HttpMethod {
    override def getName: String = "POST"
  }

  case class Put() extends HttpMethod {
    override def getName: String = "PUT"
  }

  case class Patch() extends HttpMethod {
    override def getName: String = "PATCH"
  }

  case class Delete() extends HttpMethod {
    override def getName: String = "DELETE"
  }

  final val values: List[HttpMethod] = List(Get(), Post(), Put(), Patch(), Delete())

  final val valuesByName: Map[String, HttpMethod] =
    values.foldLeft(Map.empty[String, HttpMethod])((prevState, header) => prevState + (header.getName.toUpperCase -> header))

  def getByName(name: String): HttpMethod = valuesByName.getOrElse(name.toUpperCase(), Get())

  def getByName(name: Option[String]): HttpMethod =
    name.flatMap(n => valuesByName.get(n.toUpperCase())).getOrElse(Get())

  def unify(name: Option[String]): String = getByName(name).getName

}
