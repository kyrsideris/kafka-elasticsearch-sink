package com.myawesome.sink.common

import org.slf4j.{Logger, LoggerFactory}

trait Logging {
  lazy val logger: Logger  = LoggerFactory.getLogger(s"ESS-${this.getClass.getSimpleName.dropRight(1)}")
}
