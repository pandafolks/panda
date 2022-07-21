package com.github.pandafolks.panda.routes

import com.github.pandafolks.panda.routes.entity.MappingContent

final case class RouteInfo(
                            mappingContent: MappingContent,
                            isPocket: Boolean = false,
                            isStandalone: Boolean = true
                          )

// todo mszmal: add standalone
// first search needs to be done through standalones only. If there is an api composition we will go through all.
