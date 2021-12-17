# Alternative Approaches

## Index with Sort

To enable the sorting of `ClickRecord`s based on the order they were inside partitions 
we can include the record's information regarding the partition/offset and sort 
ElasticSearch's documents based on these fields 

```json
{
  "settings": {
    "index": {
      "sort.field": [ "record.partition", "record.offset" ],
      "sort.order": [ "asc", "asc" ]
    }
  },
  "mappings": {
    "properties": {
      "record": {
        "type": "nested",
        "properties": {
          "offset": { "type": "long" },
          "partition": { "type": "integer" },
          "timestamp": { "type": "long" }
        }
      },
      "session_id": { "type": "string" },
      "browser": { "type": "string" },
      "campaign": { "type": "string" },
      "channel": { "type": "string" },
      "referrer": { "type": "string" },
      "ip": { "type": "string" }
    }
  }
}
```

## Schemaless Operation

The sink doesn't necessarily care about the shape of data. So it should operate either with the current
schema of ClickRecord, with an evolution of that schema or with a completely different topic or schema or data.
To do so the consumer can deserialise the data into Avro's IndexedRecord and insert them into ElasticSearch as
JSON literals.  
