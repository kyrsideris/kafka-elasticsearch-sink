package com.myawesome.sink.common

import org.scalatest.flatspec.AsyncFlatSpec

class ConfigurationTest extends AsyncFlatSpec {
  it should "get configuration from environment" in {

    // Environment variable SERVICE_FOO is set in build.sbt
    case class A() extends Configuration
    val a = A()
    assert(a.getConfigFromEnv("service").get("service.foo").contains("test"))
    assert(!a.getConfigFromEnv("service").contains("service.bar"))
  }

  it should "get configuration from file" in {

    // Configuration properties are set in application.properties file
    case class A() extends Configuration
    val a = A()
    assert(a.getConfigFromFile("service").get("service.foo").contains("localhost:8000"))
    assert(a.getConfigFromFile("service").get("service.url").contains("http://localhost:8080"))
    assert(a.getConfigFromFile("service").get("service.very.long.path").contains("false"))
    assert(!a.getConfigFromFile("service").contains("service.bar"))
  }

  it should "get configuration from system properties" in {

    // System properties are set in build.sbt
    case class A() extends Configuration
    val a = A()
    println("a.getConfigFromEnv: " + a.getConfigFromProps("service").mkString("\n"))

    assert(a.getConfigFromProps("service").get("service.cli").contains("false"))
    assert(!a.getConfigFromProps("service").contains("service.bar"))
  }
}
