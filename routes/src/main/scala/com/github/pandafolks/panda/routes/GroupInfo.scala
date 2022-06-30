package com.github.pandafolks.panda.routes

import org.http4s.Uri.Path

final case class GroupInfo(group: Group, prefix: Path, isPocket: Boolean = false)
