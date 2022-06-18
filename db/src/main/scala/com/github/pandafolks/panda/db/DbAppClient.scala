package com.github.pandafolks.panda.db

import cats.effect.Resource
import com.github.pandafolks.panda.participant.event.ParticipantEvent
import com.github.pandafolks.panda.user.User
import com.github.pandafolks.panda.user.token.Token
import com.pandafolks.mattszm.panda.sequence.Sequence
import monix.connect.mongodb.client.CollectionOperator
import monix.eval.Task

trait DbAppClient {

  def getParticipantEventsAndSequencesConnection: Resource[Task, (CollectionOperator[ParticipantEvent], CollectionOperator[Sequence])]

  def getUsersWithTokensConnection: Resource[Task, (CollectionOperator[User], CollectionOperator[Token])]
}
