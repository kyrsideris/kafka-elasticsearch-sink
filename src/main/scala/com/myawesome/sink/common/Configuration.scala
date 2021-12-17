package com.myawesome.sink.common

import java.util.Properties
import scala.jdk.CollectionConverters._
import scala.language.implicitConversions
import scala.util.Try

trait Configuration extends Logging {
  type ConfMap = Map[String, Any]
  val mandatoryKafkaConfig: ConfMap = Map("enable.auto.commit" -> false)

  val kafkaConfig: ConfMap = getAllConfig("kafka") ++ mandatoryKafkaConfig
  val elasticConfig: ConfMap = getAllConfig("elastic")

  private[common] def getAllConfig(prefix: String): ConfMap = {
    (getConfigFromFile(prefix) ++ getConfigFromProps(prefix) ++ getConfigFromEnv(prefix))
      .toArray
      .map{ case (k, v) => (k.substring(prefix.length + 1), v) }
      .toMap
  }

  private[common] def getConfigFromEnv(prefix: String): ConfMap = {
    sys.env
      .flatMap{ case (k, v) =>
        val lower = k.toLowerCase
        if (lower.startsWith(prefix.toLowerCase + "_") )
          lower.replace("_", ".") -> v :: Nil
        else
          None
      }
  }

  private[common] def getConfigFromFile(prefix: String): ConfMap = Try {
    val configFile = sys.props.get("config.file").getOrElse("/application.properties")
    val properties = new Properties
    properties.load(getClass.getResourceAsStream(configFile))

    properties.entrySet().asScala
      .map(pair => (pair.getKey.toString, pair.getValue))
      .toArray
      .filter(_._1.toLowerCase.startsWith(prefix.toLowerCase + ".") )
      .toMap
  }.getOrElse{
    logger warn s"Unable to load configuration file for $prefix"
    Map[String, Any]()
  }

  private[common] def getConfigFromProps(prefix: String): ConfMap = {
    sys.props.toMap
      .flatMap{ case (k, v) =>
        val lower = k.toLowerCase
        if (lower.startsWith(prefix.toLowerCase + "."))
          lower -> v :: Nil
        else
          None
      }
  }

  implicit def toProperties(conf: ConfMap): Properties = {
    val props = new Properties()
    conf.foreach{ case (k, v) => props.put(k, v) }
    props
  }
}
