package com.github.mattszm.panda.utils

sealed trait PersistenceError {
  def getMessage: String
}

case class UnsuccessfulSaveOperation(message: String) extends PersistenceError {
  override def getMessage: String = s"[${UnsuccessfulSaveOperation.getClass.getSimpleName}]: $message"
}

case class UnsuccessfulUpdateOperation(message: String) extends PersistenceError {
  override def getMessage: String = s"[${UnsuccessfulUpdateOperation.getClass.getSimpleName}]: $message"
}

case class AlreadyExists(message: String) extends PersistenceError {
  override def getMessage: String = s"[${AlreadyExists.getClass.getSimpleName}]: $message"
}

case class NotExists(message: String) extends PersistenceError {
  override def getMessage: String = s"[${NotExists.getClass.getSimpleName}]: $message"
}
