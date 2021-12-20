package com.myawesome.sink.elasticsearch

import com.myawesome.sink.common.Logging
import com.sksamuel.elastic4s.ElasticApi.RichFuture
import com.sksamuel.elastic4s.ElasticDsl.{CreateIndexHandler, createIndex}
import com.sksamuel.elastic4s._
import com.sksamuel.elastic4s.http.JavaClient

import scala.concurrent.ExecutionContext.Implicits.global


class ElasticProducer(url: String) extends AutoCloseable with Logging {
  private val props = ElasticProperties(url)
  private val elasticClient = ElasticClient(JavaClient(props))
  def execute[T, U, F[_]](t: T)(implicit
                                executor: Executor[F],
                                functor: Functor[F],
                                handler: Handler[T, U],
                                manifest: Manifest[U],
                                options: CommonRequestOptions): F[Response[U]] =
    elasticClient.execute(t)(executor, functor, handler, manifest, options)
  def close(): Unit = elasticClient.close()

  def createIndices(indicesToCreate: Set[String], shards: Int, replicas: Int): Set[String] = {
    indicesToCreate
      .flatMap { index =>
        this.execute {
          createIndex(index).shards(shards).replicas(replicas)
        }.map { response =>
          println(s"Response: $response")
          // There is a chance that the index exists when this batch is trying to create it
          // in this case consider the index successfully created.
          if (response.isError && response.error.`type` != "resource_already_exists_exception") {
            // Log the error but continue with insert
            logger error s"Failed to create index: " + index
            response.error.asException.printStackTrace()
            Nil
          }
          else {
            logger info s"Index created successfully: " + index
            index :: Nil
          }
        }.await
      }
  }
}
