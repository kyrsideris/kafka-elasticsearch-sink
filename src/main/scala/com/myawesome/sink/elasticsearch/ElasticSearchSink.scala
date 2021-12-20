package com.myawesome.sink.elasticsearch

import com.myawesome.sink.common.{Configuration, Logging, Serdes}
import com.myawesome.sink.model.ClickRecord
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits.JacksonJsonIndexable
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import org.apache.kafka.clients.consumer.{ConsumerRebalanceListener, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, StringDeserializer}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import java.util.{Collection => JCollection}
import scala.collection.mutable.{Set => MutSet}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Using}


/**
 * ElasticSearchSink app to consume data from Kafka and publish to ElasticSearch
 *   grouped into 15mins intervals and creating ElasticSearch indices based on Kafka message
 *   timestamp with format `${topic_name}_${yyyy-MM-dd-HH-mm}`.
 */
object ElasticSearchSink extends App with Logging with Configuration with Serdes {

  logger info "Kafka configuration: " + kafkaConfig.mkString(", ")

  val kafkaTopic = kafkaConfig("topic").toString
  val elasticUrl = elasticConfig("cluster.url").toString
  val timezone = elasticConfig.getOrElse("timezone", "UTC").toString.toUpperCase
  val shards = elasticConfig.getOrElse("shards", "3").toString.toInt
  val replicas = elasticConfig.getOrElse("replicas", "2").toString.toInt

  val consumerListener = new ConsumerRebalanceListener {
    override def onPartitionsRevoked(partitions: JCollection[TopicPartition]): Unit =
      logger info s"The following partition are revoked: ${partitions.asScala.mkString(", ")}"

    override def onPartitionsAssigned(partitions: JCollection[TopicPartition]): Unit =
      logger info s"The following partition are assigned: ${partitions.asScala.mkString(", ")}"
  }

  import com.sksamuel.elastic4s.ElasticDsl._

  implicit val formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd-HH-mm")
    .withZone(ZoneId.of(timezone))

  Using.Manager { use =>
    val keyDeserializer: Deserializer[String] = use(new StringDeserializer())
    val valueDeserializer: Deserializer[ClickRecord] = use(reflectionAvroDeserializer4S[ClickRecord])
    valueDeserializer.configure(kafkaConfig.asJava, false)

    val consumer = use(new KafkaConsumer[String, ClickRecord](kafkaConfig, keyDeserializer, valueDeserializer))
    val producer = use(new ElasticProducer(elasticUrl))
    var indicesCreated = MutSet[String]()
    // hold indices of a week, plus 6 hours,
    // TODO: configure by using Kafka's retention period
    val numIndicesToRemember = (7 * 24 + 6) * 60 / 15

    consumer.subscribe(List(kafkaTopic).asJava, consumerListener)

    val balancer = Balancer()

    while(true) {
      val records = consumer.poll(balancer.timeout).asScala.toArray
      if (!records.isEmpty)
        balancer.time {
          val indicesInRecords = records.map(r => formatIndex(kafkaTopic, r.timestamp())).toSet
          val indicesToCreate = indicesInRecords -- indicesCreated

          // Given indices to create, get indices actually created
          val indicesNew = producer.createIndices(indicesToCreate, shards, replicas)

          // Add newly created indices to the set of already created
          indicesCreated ++= indicesNew
          indicesCreated = trimIndices(indicesCreated, numIndicesToRemember)

          val offsets = records
            .groupBy(record => (record.topic(), record.partition()))
            .map { case ((topic, partition), partitionRecords) =>
              val records = partitionRecords.sortBy(_.offset()) // Maybe redundant

              logger debug s"Indexing records " +
                s"for topic=$topic, partition=$partition " +
                s"offsets=[${records.head.offset()}, ${records.last.offset()}]"

              producer.execute {
                bulk(
                  records.map(r =>
                    IndexRequest(index = formatIndex(kafkaTopic, r.timestamp()))
                      .doc(r.value()))
                )
              }.transform {
                case Success(response) if response.isSuccess =>
                  Success(
                    new TopicPartition(topic, partition) -> new OffsetAndMetadata(records.last.offset())
                  )

                case Success(response) if response.isError =>
                  logger error s"Error indexing records " +
                    s"for topic=$topic, partition=$partition " +
                    s"offsets=[${records.head.offset()}, ${records.last.offset()}] " +
                    s"Error=${response.error.`type`} ${response.error.reason}"
                  Failure(response.error.asException)

                case Failure(exception) =>
                  logger error s"Error indexing records " +
                    s"for topic=$topic, partition=$partition " +
                    s"offsets=[${records.head.offset()}, ${records.last.offset()}] " +
                    s"Exception=${exception.toString}"
                  exception.printStackTrace()
                  Failure(exception)
              }
            }
            .collect(_.await)
            .toMap.asJava

          // Commit in one go outside of inner threads because
          // KafkaConsumer is not safe for multi-threaded access
          consumer.commitSync(offsets)
        }

      logger debug s"Balancing: block=${balancer.block}, idle=${balancer.idle}"
      balancer.sleep()
    }
  } match {
    case Success(_) =>
      logger info "Closing sink"
    case Failure(e) =>
      logger error s"Exception was thrown during operation: ${e.getMessage}"
      e.printStackTrace()
  }

  private def findStartOfQuarter(timestampMs: Long): Long = {
    timestampMs - (timestampMs % (15*60*1000))
  }

  private def formatIndex(topic: String, timestamp: Long)(implicit formatter: DateTimeFormatter): String = {
    topic + "_" + formatter.format(
      Instant.ofEpochMilli(findStartOfQuarter(timestamp))
    )
  }

  private def trimIndices(indicesCreated: MutSet[String], numRemember: Int): MutSet[String] = {
    if (indicesCreated.size > numRemember)
      indicesCreated.toArray.sorted.takeRight(numRemember).to(MutSet)
    else
      indicesCreated
  }
}
