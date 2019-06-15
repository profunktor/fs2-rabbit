package dev.profunktor.fs2rabbit

import scala.collection.convert.{DecorateAsJava, DecorateAsScala}

object javaConversion extends DecorateAsJava with DecorateAsScala
