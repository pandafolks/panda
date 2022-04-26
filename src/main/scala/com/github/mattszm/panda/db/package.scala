package com.github.mattszm.panda

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, SimpleStatement}

import java.util.concurrent.CompletionStage
import scala.jdk.FutureConverters._

package object db {
  implicit def listenableFutureToFuture[T](listenableFuture: CompletionStage[T]): scala.concurrent.Future[T] =
    listenableFuture.asScala

  implicit class CqlStrings(val context: StringContext) extends AnyVal {
    def cql(args: Any*)(implicit session: CqlSession): scala.concurrent.Future[PreparedStatement] = {
      val statement = SimpleStatement.newInstance(context.raw(args: _*))
      session.prepareAsync(statement)
    }
  }
}
