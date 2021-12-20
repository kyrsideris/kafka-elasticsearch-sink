package com.myawesome.sink.elasticsearch

import com.dimafeng.testcontainers.{ElasticsearchContainer, ForEachTestContainer}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.{be, contain, have}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.testcontainers.utility.DockerImageName


class ElasticProducerTest extends AnyFlatSpec with ForEachTestContainer {

  override val container: ElasticsearchContainer = ElasticsearchContainer(
    DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:7.8.0")
  )
  container.start()

  behavior of "ElasticProducer"

  it should "create indices" in {
    val url = "http://" + container.httpHostAddress
    val producer = new ElasticProducer(url)
    val created = producer.createIndices(indicesToCreate = Set("a", "b", "c"), shards = 1, replicas = 1)

    created should (have size 3 and contain theSameElementsAs Set("a", "b", "c"))
  }

  it should "not fail when creates indices that exist" in {
    val url = "http://" + container.httpHostAddress

    val producer = new ElasticProducer(url)
    val created1st = producer.createIndices(indicesToCreate = Set("d", "e", "f"), shards = 1, replicas = 1)
    created1st should (have size 3 and contain theSameElementsAs Set("d", "e", "f"))
    val created2nd = producer.createIndices(indicesToCreate = Set("f", "g", "h"), shards = 1, replicas = 1)

    created2nd should (have size 3 and contain theSameElementsAs Set("f", "g", "h"))
  }

  it should "fail when creates indices that contain illegal character" in {
    val url = "http://" + container.httpHostAddress

    val producer = new ElasticProducer(url)
    val created1st = producer.createIndices(indicesToCreate = Set("k", "l", "m:"), shards = 1, replicas = 1)

    created1st should (have size 2 and contain theSameElementsAs Set("k", "l"))
  }
}
