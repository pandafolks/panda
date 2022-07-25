package com.github.pandafolks.panda.routes.filter

import com.github.pandafolks.panda.routes.entity.Mapper
import com.github.pandafolks.panda.routes.payload.MapperRecordPayload

trait StandaloneFilter {
  def filter(mapper: Mapper): Boolean
  def filter(mapper: MapperRecordPayload): Boolean
}

object StandaloneFilter {

  case object StandaloneOnly extends StandaloneFilter {
    override def filter(mapper: Mapper): Boolean = mapper.isStandalone

    override def filter(mapper: MapperRecordPayload): Boolean = mapper.isStandalone.getOrElse(true)
  }

  case object NonStandaloneOnly extends StandaloneFilter {
    override def filter(mapper: Mapper): Boolean = !mapper.isStandalone

    override def filter(mapper: MapperRecordPayload): Boolean = !mapper.isStandalone.getOrElse(true)
  }

  case object All extends StandaloneFilter {
    override def filter(mapper: Mapper): Boolean = true

    override def filter(mapper: MapperRecordPayload): Boolean = true
  }

  def getFromOptional(standaloneFilter: Option[StandaloneFilter]): StandaloneFilter = standaloneFilter.getOrElse(All)
}
