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
