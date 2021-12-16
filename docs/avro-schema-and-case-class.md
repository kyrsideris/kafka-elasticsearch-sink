# Avro Schema and Case Class

Given the following schema 

```json
{
  "type": "record",
  "name": "ClickRecord",
  "fields": [
    { "name": "session_id", "type": "string" },
    { "name": "browser",    "type": ["string", "null"] },
    { "name": "campaign",   "type": ["string", "null"] },
    { "name": "channel",    "type": "string" },
    { "name": "referrer",   "type": ["string", "null"], "default": "None" },
    { "name": "ip",         "type": ["string", "null"] }
  ]
}
```

The corresponding case class is:

```scala
final case class ClickRecord(
  session_id: String, 
  browser: Option[String], 
  campaign: Option[String], 
  channel: String, 
  referrer: Option[String] = Some("None"), 
  ip: Option[String]
)
```

If the team is willing to use only Scala as a language of choice then
packaging the schemas as jar artifacts with Scala's case classes will
allow the team to express more complex relations. In this case, the 
schemas can be generated from case class using libraries like `avro4s`.

But most teams are choosing to rely on Avro schemas as a way of defining
their model and generate the Scala's case classes using sbt plugins like  
`sbt-avrohugger`, or in the case of Java and Maven the `avro-maven-plugin`.

For sbt the plugin can be added as:

```sbt
addSbtPlugin("com.julianpeeters" % "sbt-avrohugger" % "2.0.0-RC24")
```
