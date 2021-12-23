package com.myawesome.sink.elasticsearch

import com.myawesome.sink.common.{Configuration, Logging, Serdes}
import com.myawesome.sink.model.ClickRecord
import org.apache.kafka.clients.consumer.{ConsumerRebalanceListener, KafkaConsumer, OffsetAndMetadata}
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, IntegerSerializer, Serializer, StringDeserializer}

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.{Collection => JCollection}
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Using}


/**
 * KafkaSink app to consume data from Kafka and publish to Kafka
 */
object KafkaSink extends App with Logging with Configuration with Serdes {

  logger info "Kafka configuration: " + kafkaConfig.mkString(", ")

  val kafkaTopicConsume = kafkaConfig("topic.consume").toString
  val kafkaTopicProduce = kafkaConfig("topic.produce").toString
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

  implicit val formatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("yyyy-MM-dd-HH-mm")
    .withZone(ZoneId.of(timezone))

  Using.Manager { use =>
    val keyDeserializer: Deserializer[String] = use(new StringDeserializer())
    val valueDeserializer: Deserializer[ClickRecord] = use(reflectionAvroDeserializer4S[ClickRecord])
    val keySerializer: Serializer[Integer] = use(new IntegerSerializer())
    val valueSerializer: Serializer[ClickRecord] = use(reflectionAvroSerializer4S[ClickRecord])

    keyDeserializer.configure(kafkaConfig.asJava, true)
    keySerializer.configure(kafkaConfig.asJava, true)
    valueDeserializer.configure(kafkaConfig.asJava, false)
    valueSerializer.configure(kafkaConfig.asJava, false)

    val consumer = use(new KafkaConsumer[String, ClickRecord](kafkaConfig, keyDeserializer, valueDeserializer))
    val producer = use(new KafkaProducer[Integer, ClickRecord](kafkaConfig, keySerializer, valueSerializer))

    // TODO: configure by using Kafka's retention period

    consumer.subscribe(List(kafkaTopicConsume).asJava, consumerListener)

    val balancer = Balancer()

    while(true) {
      val records = consumer.poll(balancer.timeout).asScala.toArray
      if (!records.isEmpty)
        balancer.time {
          records
            .map { record =>
              val producerRecord = new ProducerRecord[Integer, ClickRecord](
                kafkaTopicProduce, Integer.parseInt(record.key().stripPrefix("\"").stripSuffix("\"")), record.value()
              )
              producer.send(producerRecord, new SendCallback(consumer))
            }
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

  class SendCallback[K, V](consumer: KafkaConsumer[K, V]) extends Callback with Logging {
    def onCompletion(metadata: RecordMetadata, exception: Exception) = {
      if (exception != null) {
        consumer.commitSync(Map(
          new TopicPartition(metadata.topic(), metadata.partition()) -> new OffsetAndMetadata(metadata.offset())
        ).asJava)
        logger debug s"Committed ${metadata.topic()}, ${metadata.partition()}, ${metadata.offset()}"
      }
    }
  }
}
