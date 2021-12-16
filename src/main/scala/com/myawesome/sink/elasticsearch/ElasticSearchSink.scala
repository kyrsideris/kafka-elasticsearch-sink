package com.myawesome.sink.elasticsearch

import com.myawesome.sink.common.{Configuration, HelperSerdes, Logging}
import com.myawesome.sink.model.ClickRecord
import com.sksamuel.elastic4s.jackson.ElasticJackson.Implicits._
import com.sksamuel.elastic4s.requests.indexes.IndexRequest
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.kafka.clients.consumer.{ConsumerRebalanceListener, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import java.time.{Instant, ZoneId, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.{Collection => JCollection}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Using}

object ElasticSearchSink extends App with Logging with Configuration with HelperSerdes {

//  val configFile = sys.props.get("config.file").getOrElse(getClass.getResource("/consumer.conf").getPath)
//
//  val usage = """
//    |Usage: ElasticSearchSink  arg2 arg3
//    |
//    | arg1: arg1
//    | arg2: arg2
//    | arg3: arg3
//    |
//    |""".stripMargin
//  if (args.length < 2 || args.length > 3) {
//    println(usage)
//    sys.exit(1)
//  }


  val kafkaConfigAdd = kafkaConfig ++ Map(
    "key.deserializer" -> classOf[StringDeserializer].getCanonicalName,
    "value.deserializer" -> classOf[KafkaAvroDeserializer].getCanonicalName
  )
  logger info "Kafka configuration: " + kafkaConfigAdd.mkString(", ")

  val consumerListener = new ConsumerRebalanceListener {
    override def onPartitionsRevoked(partitions: JCollection[TopicPartition]): Unit =
      logger info s"The following partition are revoked: ${partitions.asScala.mkString(", ")}"

    override def onPartitionsAssigned(partitions: JCollection[TopicPartition]): Unit =
      logger info s"The following partition are assigned: ${partitions.asScala.mkString(", ")}"
  }

  import com.sksamuel.elastic4s.ElasticDsl._
  val elasticUrl = elasticConfig("cluster.url").toString

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm").withZone( ZoneOffset.UTC )

  Using.Manager { use =>
    val consumer = use(new KafkaConsumer[String, ClickRecord](kafkaConfigAdd))
    val producer = use(new ElasticProducer(elasticUrl))

    consumer.subscribe(List("click").asJava, consumerListener)

    val balancer = Balancer()

    while(true) {
      val records = consumer.poll(balancer.timeout).asScala.toArray
      if (!records.isEmpty)
        balancer.time {
          val earliestTimestamp = records.map(_.timestamp()).min
          val startOfQuarter = Instant.ofEpochMilli(findStartOfQuarter(earliestTimestamp))

          producer.createClickIndex("click_" + formatter.format(startOfQuarter))

          records
            .groupBy(record => (record.topic(), record.partition()))
            .foreach { case (group, partitionRecords) =>
              val topic = group._1
              val partition = group._2
              val records = partitionRecords.sortBy(_.offset()) // Maybe redundant

              logger debug s"Indexing records " +
                s"for topic=$topic, partition=$partition " +
                s"offsets=[${records.head.offset()}, ${records.last.offset()}]"

              val indexRequests = records.map(r => IndexRequest(index = "click").doc(r.value()))

              producer.execute {
                bulk(indexRequests)
              }.onComplete {
                case Success(response) if response.isSuccess =>
                  val offsets = Map(
                    new TopicPartition(topic, partition) -> new OffsetAndMetadata(records.last.offset())
                  ).asJava
                  consumer.commitSync(offsets)

                case Success(response) if !response.isSuccess =>
                  logger error s"Error indexing records " +
                    s"for topic=$topic, partition=$partition " +
                    s"offsets=[${records.head.offset()}, ${records.last.offset()}]"

                case Failure(exception) =>
                  logger error s"Error indexing records " +
                    s"for topic=$topic, partition=$partition " +
                    s"offsets=[${records.head.offset()}, ${records.last.offset()}] " +
                    s"Exception=${exception.toString}"
                  exception.printStackTrace()
              }
            }
        }

      logger debug s"Balancing: block=${balancer.block}, idle=${balancer.idle}"
      balancer.sleep()
    }
  }

  def findStartOfQuarter(timestampMs: Long): Long = {
    timestampMs - (timestampMs % (15*60*1000))
  }

}
