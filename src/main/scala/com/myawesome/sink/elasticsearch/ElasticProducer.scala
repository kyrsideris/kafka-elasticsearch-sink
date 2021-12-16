package com.myawesome.sink.elasticsearch

import com.myawesome.sink.common.Logging
import com.sksamuel.elastic4s.ElasticApi.RichFuture
import com.sksamuel.elastic4s.ElasticDsl.{CreateIndexHandler, GetIndexHandler, createIndex, getIndex, properties, textField}
import com.sksamuel.elastic4s.http.JavaClient
import com.sksamuel.elastic4s._

import scala.concurrent.ExecutionContext.Implicits.global


class ElasticProducer(url: String) extends AutoCloseable with Logging {
  private val props = ElasticProperties(url)
  private val elasticClient = ElasticClient(JavaClient(props))
  def show[T](t: T)(implicit handler: Handler[T, _]): String =
    elasticClient.show(t)(handler)
  def execute[T, U, F[_]](t: T)(implicit
                                executor: Executor[F],
                                functor: Functor[F],
                                handler: Handler[T, U],
                                manifest: Manifest[U],
                                options: CommonRequestOptions): F[Response[U]] =
    elasticClient.execute(t)(executor, functor, handler, manifest, options)
  override def close(): Unit = elasticClient.close()

  def createClickIndex(index: String) = {
    val creation = this.execute {
      getIndex(index)
    }.map {
      case RequestFailure(_, _, _, error) if error.reason.startsWith("no such index") =>
        this.execute {
          createIndex(index)
            .shards(3).replicas(2)
            .mapping(
              properties(
                textField("session_id"),
                textField("browser"),
                textField("campaign"),
                textField("channel"),
                textField("referrer"),
                textField("ip")
              )
            )
        }.map {
          case RequestFailure(_, _, _, error) =>
            logger error s"Failed to create ElasticSearch index=$index: ${error.reason}"
            false
          case RequestSuccess(_, _, _, _) =>
            logger info s"Successfully created ElasticSearch index=$index"
            true
        }.await
      case RequestFailure(_, _, _, error) =>
        logger error s"Failed to get ElasticSearch index=$index: ${error.reason}"
        false
      case RequestSuccess(_, _, _, _) =>
        logger info s"Successfully retrieved ElasticSearch index=$index"
        true
    }.await

    if (!creation) {
      logger error s"Index creation failed for index=$index"
      sys.exit(1)
    }
  }


}
