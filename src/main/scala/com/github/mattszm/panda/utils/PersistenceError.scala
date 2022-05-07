package com.github.mattszm.panda.utils

sealed trait PersistenceError

case class UnsuccessfulSaveOperation(message: String) extends PersistenceError

case class AlreadyExists(message: String) extends PersistenceError
