package com.myawesome.sink.common

import org.scalatest.flatspec.AsyncFlatSpec

class ConfigurationTest extends AsyncFlatSpec {
  it should "get configuration from file" in {

    // Configuration properties are set in application.properties file
    case class A() extends Configuration
    val conf = A().getConfigFromFile("service")

    assert(conf.get("service.foo").contains("localhost:8000"))
    assert(conf.get("service.url").contains("http://localhost:8080"))
    assert(conf.get("service.very.long.path").contains("false"))
    assert(!conf.contains("service.bar"))
  }

  it should "get configuration from system properties" in {

    // System properties are set in build.sbt
    case class A() extends Configuration
    val conf = A().getConfigFromProps("service")

    assert(conf.get("service.cli").contains("false"))
    assert(!conf.contains("service.bar"))
  }

  it should "get configuration from environment" in {

    // Environment variable SERVICE_FOO is set in build.sbt
    case class A() extends Configuration
    val conf = A().getConfigFromEnv("service")

    assert(conf.get("service.foo").contains("test"))
    assert(!conf.contains("service.bar"))
  }

  it should "get all configuration" in {

    // Gather all configuration are set in build.sbt
    case class A() extends Configuration
    val conf = A().getAllConfig("service")

    assert(conf.get("url").contains("http://localhost:8080"))
    assert(conf.get("very.long.path").contains("false"))
    assert(conf.get("cli").contains("false"))
    assert(conf.get("foo").contains("test"))
    assert(!conf.contains("bar"))
  }
}
