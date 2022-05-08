package com.github.mattszm.panda.db

import cats.effect.Resource
import com.github.mattszm.panda.participant.event.ParticipantEvent
import com.github.mattszm.panda.sequence.Sequence
import com.github.mattszm.panda.user.User
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait DbAppClient {
  def getConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]
}
