package dev.profunktor.fs2rabbit.testing

import dev.profunktor.fs2rabbit.model.AmqpFieldValue.DecimalVal
import org.scalacheck._

import java.time.Instant
import java.util.Date

object RabbitStdDataArbs {

  implicit val bigDecimalArb: Arbitrary[BigDecimal] =
    Arbitrary(Arbitrary.arbBigDecimal.arbitrary.filter(DecimalVal.from(_).isDefined))

  implicit val bigIntArb: Arbitrary[BigInt] =
    Arbitrary(bigDecimalArb.arbitrary.map(_.toBigInt))

  implicit val arbInstant: Arbitrary[Instant] =
    Arbitrary(Arbitrary.arbInstant.arbitrary.map(_.truncatedTo(java.time.temporal.ChronoUnit.SECONDS)))

  implicit val arbDate: Arbitrary[Date] =
    Arbitrary(
      Arbitrary.arbDate.arbitrary
        .map(_.toInstant.truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
        .map(Date.from)
    )

}
