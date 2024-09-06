package dev.profunktor.fs2rabbit.testing

import dev.profunktor.fs2rabbit.model.AmqpFieldValue._
import dev.profunktor.fs2rabbit.model.{AmqpFieldValue, AmqpProperties, DeliveryMode, Headers, ShortString}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._
import scodec.bits.ByteVector

import java.util.Date

object AmqpPropertiesArbs {

  implicit val bigDecimalVal: Arbitrary[DecimalVal] = Arbitrary[DecimalVal] {
    for {
      unscaledValue <- arbitrary[Int]
      scale         <- Gen.choose(0, 255)
    } yield {
      val javaBigDecimal = new java.math.BigDecimal(BigInt(unscaledValue).bigInteger, scale)
      DecimalVal.unsafeFrom(BigDecimal(javaBigDecimal))
    }
  }

  implicit val dateVal: Arbitrary[TimestampVal] = Arbitrary[TimestampVal] {
    arbitrary[Date].map(TimestampVal.from)
  }

  private def modTruncateString(str: String): ShortString = {
    val newLength = str.length % (ShortString.MaxByteLength + 1)
    ShortString.unsafeFrom(str.substring(newLength))
  }

  def tableVal(maxDepth: Int): Arbitrary[TableVal] = Arbitrary {
    for {
      keys            <- arbitrary[List[String]]
      keysWithValueGen = keys.map(key => amqpHeaderVal(maxDepth).arbitrary.map(modTruncateString(key) -> _))
      keyValues       <- Gen.sequence[List[(ShortString, AmqpFieldValue)], (ShortString, AmqpFieldValue)](keysWithValueGen)
    } yield TableVal(keyValues.toMap)
  }

  implicit val byteVal: Arbitrary[ByteVal] = Arbitrary {
    arbitrary[Byte].map(ByteVal.apply)
  }

  implicit val doubleVal: Arbitrary[DoubleVal] = Arbitrary {
    arbitrary[Double].map(DoubleVal.apply)
  }

  implicit val floatVal: Arbitrary[FloatVal] = Arbitrary {
    arbitrary[Float].map(FloatVal.apply)
  }

  implicit val shortVal: Arbitrary[ShortVal] = Arbitrary {
    arbitrary[Short].map(ShortVal.apply)
  }

  implicit val byteArrayVal: Arbitrary[ByteArrayVal] = Arbitrary {
    arbitrary[Array[Byte]].map(xs => ByteArrayVal(ByteVector(xs)))
  }

  implicit val booleanVal: Arbitrary[BooleanVal] = Arbitrary {
    arbitrary[Boolean].map(BooleanVal.apply)
  }

  implicit val intVal: Arbitrary[IntVal] = Arbitrary[IntVal] {
    Gen.posNum[Int].flatMap(x => IntVal(x))
  }

  implicit val longVal: Arbitrary[LongVal] = Arbitrary[LongVal] {
    Gen.posNum[Long].flatMap(x => LongVal(x))
  }

  implicit val stringVal: Arbitrary[StringVal] = Arbitrary[StringVal] {
    Gen.alphaStr.flatMap(x => StringVal(x))
  }

  def arrayVal(maxDepth: Int): Arbitrary[ArrayVal] = Arbitrary {
    implicit val implicitAmqpHeaderVal: Arbitrary[AmqpFieldValue] = amqpHeaderVal(maxDepth)
    arbitrary[Vector[AmqpFieldValue]].map(ArrayVal.apply)
  }

  implicit val nullVal: Arbitrary[NullVal.type] = Arbitrary {
    Gen.const(NullVal)
  }

  implicit val implicitAmqpHeaderVal: Arbitrary[AmqpFieldValue] = Arbitrary {
    // Cap it at 2 so that we don't have Stack Overflows/long test times
    amqpHeaderVal(2).arbitrary
  }

  def amqpHeaderVal(maxDepth: Int): Arbitrary[AmqpFieldValue] = Arbitrary[AmqpFieldValue] {
    val nonRecursiveGenerators = List(
      bigDecimalVal.arbitrary,
      dateVal.arbitrary,
      byteVal.arbitrary,
      doubleVal.arbitrary,
      floatVal.arbitrary,
      shortVal.arbitrary,
      byteArrayVal.arbitrary,
      booleanVal.arbitrary,
      intVal.arbitrary,
      longVal.arbitrary,
      stringVal.arbitrary,
      nullVal.arbitrary
    )

    if (maxDepth <= 0) {
      // This is because Gen.oneOf is overloaded and we need to access its three-argument version
      Gen.oneOf(nonRecursiveGenerators(0), nonRecursiveGenerators(1), nonRecursiveGenerators.drop(2): _*)
    } else {
      val allGenerators = tableVal(maxDepth - 1).arbitrary :: arrayVal(maxDepth - 1).arbitrary :: nonRecursiveGenerators
      Gen.lzy(
        Gen.oneOf(allGenerators(0), allGenerators(1), allGenerators.drop(2): _*)
      )
    }
  }

  private val headersGen: Gen[Headers] =
    Gen
      .mapOf[String, AmqpFieldValue](
        for {
          key   <- Gen.alphaStr
          value <- arbitrary[AmqpFieldValue]
        } yield (key, value)
      )
      .map(Headers(_))

  implicit val amqpProperties: Arbitrary[AmqpProperties] = Arbitrary[AmqpProperties] {
    for {
      contentType     <- Gen.option(Gen.alphaStr)
      contentEncoding <- Gen.option(Gen.alphaStr)
      priority        <- Gen.option(Gen.posNum[Int])
      deliveryMode    <- Gen.option(Gen.oneOf(1, 2))
      correlationId   <- Gen.option(Gen.alphaNumStr)
      messageId       <- Gen.option(Gen.alphaNumStr)
      messageType     <- Gen.option(Gen.alphaStr)
      userId          <- Gen.option(Gen.alphaNumStr)
      appId           <- Gen.option(Gen.alphaNumStr)
      expiration      <- Gen.option(Gen.alphaNumStr)
      replyTo         <- Gen.option(Gen.alphaNumStr)
      clusterId       <- Gen.option(Gen.alphaNumStr)
      timestamp       <- Gen.option(dateVal.arbitrary.map(_.instantWithOneSecondAccuracy))
      headers         <- headersGen
    } yield AmqpProperties(
      contentType,
      contentEncoding,
      priority,
      deliveryMode.map(DeliveryMode.from),
      correlationId,
      messageId,
      messageType,
      userId,
      appId,
      expiration,
      replyTo,
      clusterId,
      timestamp,
      headers
    )
  }

}
