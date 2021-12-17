# Kafka to ElasticSearch Sink

## Environment

| tool  | version       |
|-------|---------------|
| java  | 1.8.0_181-b13 |
| scala | 2.13.3        |

## Documentation

TBC

## Development

```shell
sbt clean compile test
```

User can create an uber-jar as:

```shell
sbt assembly
```

This project uses Jib to create a docker image that encapsulates the `ElasticSearchSink` app.

```shell
sbt clean compile jibDockerBuild
```

It will create a new image and tag it with the project's version, here being 
`registry.myawesome.com/docker/kafka-to-elasticsearch:1.0.0`. The creation time is set to linux epoch 0 by default.
The previously created image for the project will be untagged. 
Check [Clean up docker Environment](###clean-up-docker-environment) to see how to clean up.

```shell
REPOSITORY                                             TAG       IMAGE ID       CREATED         SIZE
landoop/fast-data-dev                                  latest    a072c20b3af9   2 months ago    1.34GB
docker.elastic.co/elasticsearch/elasticsearch          7.8.0     121454ddad72   18 months ago   810MB
<none>                                                 <none>    f3633bb0d30c   51 years ago    247MB
registry.myawesome.com/docker/kafka-to-elasticsearch   1.0.0     d13e52e113f3   51 years ago    247MB
```

### Experimentation in Docker Compose

Make sure that `ADV_HOST` is commented out in `fast-data-dev` service of [compose.yml](docker/compose.yml).

```shell
# Remove previous run
docker-compose -f docker/compose.yml down

# Bring up Kafka/Zookeeper/Schema Registry/ElasticSearch
docker-compose -f docker/compose.yml up fast-data-dev elasticsearch
```

In a different shell start the continuous producer: 

```shell
docker-compose -f docker/compose.yml up producer
```

You can visualise the stream of data with Kafka Avro Consumer console app:

```shell
# Open a shell inside `fast-data-dev`
docker exec -it docker_fast-data-dev_1 bash
```

Consume messages with `kafka-avro-console-consumer`:

```shell
kafka-avro-console-consumer --bootstrap-server fast-data-dev:9092 --topic click \
  --property "print.key=true" \
  --property "key.separator= => " \
  --key-deserializer org.apache.kafka.common.serialization.StringDeserializer \
  --value-deserializer io.confluent.kafka.serializers.KafkaAvroDeserializer
```

You would see something like this:

```shell
174833 => {"session_id":"174833-2021-12-16T23:08:49.082","browser":{"string":"b-Tak6B7rB"}, 
  "campaign":{"string":"c-y9krK61n"},"channel":"h-z75vug/d","referrer":{"string":"r-RZeHAB56"},
  "ip":{"string":"0.0.0.0"}}

174834 => {"session_id":"174834-2021-12-16T23:08:49.411","browser":{"string":"b-mhgUCB5n"},
  "campaign":{"string":"c-WeGC+HKe"},"channel":"h-Ceb+QhNh","referrer":{"string":"r-LiZrH7xa"},
  "ip":{"string":"0.0.0.0"}}

174836 => {"session_id":"174836-2021-12-16T23:08:50.070","browser":{"string":"b-c+31enof"},
  "campaign":{"string":"c-UIPD2Nnr"},"channel":"h-0QmiRV3K","referrer":{"string":"r-jOrmx40y"},
  "ip":{"string":"0.0.0.0"}}
```

In a different shell, keep an eye on ElasticSearch's indices.

```shell
watch -n 3 'curl -s -X GET "localhost:9200/_cat/indices" |  sort -k 3 -r'
```

Example output

```shell
yellow open click_2021-12-17-09-45 XPLWYz6lQZaNrCobLv224Q 3 2  91 0 157.3kb 157.3kb
yellow open click_2021-12-17-09-30 B_av3Us_ThqX-CErpf4lpg 3 2 881 0 410.4kb 410.4kb
...
yellow open click_2021-12-16-16-00 RLmlqLZjQDKjoExOcysq6Q 3 1 2713 0 544.1kb 544.1kb
yellow open click_2021-12-16-15-45 8k5dqQ1qQ6K8OsXDDYdxFQ 3 1 2704 0 545.7kb 545.7kb
yellow open click_2021-12-16-15-30 vvda3YWmSCyNe85r_asLzg 3 1 2659 0 561.2kb 561.2kb
```

Given the created indices we can query the inserted docs:

```shell
curl -X GET "localhost:9200/click_2021-12-17-09-30/_search?pretty" -H 'Content-Type: application/json' -d'
{
  "query": {
    "wildcard": {
      "session_id": "*"
    }
  }
}
'
```

The output looks like this:

```shell
{
  "took" : 924,
  "timed_out" : false,
  "_shards" : {
    "total" : 3,
    "successful" : 3,
    "skipped" : 0,
    "failed" : 0
  },
  "hits" : {
    "total" : {
      "value" : 881,
      "relation" : "eq"
    },
    "max_score" : 1.0,
    "hits" : [
      {
        "_index" : "click_2021-12-17-09-30",
        "_type" : "_doc",
        "_id" : "4lLDx30BzOR1gSBNc0nm",
        "_score" : 1.0,
        "_source" : {
          "session_id" : "158-2021-12-17T09:40:11.835",
          "browser" : "b-fpTb+WJx",
          "campaign" : "c-rR97KxaS",
          "channel" : "h-aEFIeQvW",
          "referrer" : "r-+yRgVeaK",
          "ip" : "0.0.0.0"
        }
      },
      {
        "_index" : "click_2021-12-17-09-30",
        "_type" : "_doc",
        "_id" : "5VLDx30BzOR1gSBNc0nn",
        "_score" : 1.0,
        "_source" : {
          "session_id" : "161-2021-12-17T09:40:12.814",
          "browser" : "b-iwf/hmoI",
          "campaign" : "c-xk9mqtGn",
          "channel" : "h-wZPBHiDf",
          "referrer" : "r-qpzOh95i",
          "ip" : "0.0.0.0"
        }
      },
...
      {
        "_index" : "click_2021-12-17-09-30",
        "_type" : "_doc",
        "_id" : "EFLDx30BzOR1gSBNo0rY",
        "_score" : 1.0,
        "_source" : {
          "session_id" : "204-2021-12-17T09:40:26.839",
          "browser" : "b-CzjtPudQ",
          "campaign" : "c-t/yS+8nb",
          "channel" : "h-zlFdrlLz",
          "referrer" : "r-PMze82vC",
          "ip" : "0.0.0.0"
        }
      }
    ]
  }
}
```

### Experimentation in Host Machine

If you want to experiment via running your code on your local machine, then there are two options:

1) Allow Kafka to advertise its brokers as localhost by adding (commenting in) the following 
   environment variable in fast-data-dev:
    ```shell
    ADV_HOST: "127.0.0.1"
    ```
   For the experimentation using a local setup, the user can create an uber-jar 
   using the `assembly` sbt plugin, see command above. The jar output will be located under
   `target/scala-2.13/kafka-to-elasticsearch-assembly-1.0.0.jar` and it can be executed with the included
   IntelliJ IDEA configuration located in [Run jar](.run/Run%20jar.run.xml).

2) Enable remote debugging by exporting the debugger port.
   Read more here: [Java Application Remote Debugging](https://www.baeldung.com/java-application-remote-debugging)

   
### Clean up Docker Environment

```shell
docker ps -q -f 'status=exited'
docker volume prune -f
docker network prune -f
docker system prune -f
```

Older docker images can be removed using the following command:

```shell
docker images | grep "<none>" | tr -s ' ' ' ' | cut -d " " -f 3 | \
  xargs -I % sh -c 'docker rmi -f %'
```
