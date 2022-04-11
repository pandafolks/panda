package com.mattszm.panda.participants

import com.mattszm.panda.routes.Group

final case class Participant(host: String, port: Int, group: Group, identifier: Option[String] = Option.empty)
