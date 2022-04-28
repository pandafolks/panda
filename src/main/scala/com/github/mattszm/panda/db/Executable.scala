package com.github.mattszm.panda.db

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{AsyncResultSet, PreparedStatement, Row}
import monix.eval.Task
import monix.reactive.Observable

import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._


object Executable {

  def query(cql: Future[PreparedStatement], parameters: Any*)(implicit session: CqlSession): Observable[Row] =
    Observable
      .fromTask(Task.deferFuture(cql))
      .mapEvalF(p => session.executeAsync(
        p.bind(parameters.map(_.asInstanceOf[Object]): _*)).asScala)
      .flatMap(Observable.fromAsyncStateAction((rs: AsyncResultSet) => page(rs).map((_, rs)))(_))
      .takeWhile(_.nonEmpty)
      .flatMap(Observable.fromIterable)

  private def page(rs: AsyncResultSet): Task[Iterable[Row]] = Task.defer {
    val page = rs.currentPage().asScala
    if (rs.hasMorePages)
      Task.from(rs.fetchNextPage().asScala).map(_ => page)
    else
      Task.now(page)
  }
}
