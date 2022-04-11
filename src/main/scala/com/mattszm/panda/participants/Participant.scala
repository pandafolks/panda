package com.mattszm.panda.participants

final case class Participant(host: String, port: Int, group: String, identifier: Option[String] = Option.empty)
