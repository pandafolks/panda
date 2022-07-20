package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.MappingContent
import org.http4s.Uri.Path

final case class RouteInfo(mappingContent: MappingContent, prefix: Path, isPocket: Boolean = false)
//todo mszmal: idea -> there should be no group, but Either[Group, MappingContent] ???
