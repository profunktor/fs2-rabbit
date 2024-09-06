package dev.profunktor.fs2rabbit.testing

import cats.data.{NonEmptyList, NonEmptySeq}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck._

object CatsCollectionsArbs{

  implicit def arbNel[T: Arbitrary]: Arbitrary[NonEmptyList[T]] = Arbitrary {
    for {
      head <- arbitrary[T]
      tail <- arbitrary[List[T]]
    } yield NonEmptyList(head, tail)
  }

  implicit def arbNes[T: Arbitrary]: Arbitrary[NonEmptySeq[T]] = Arbitrary {
    for {
      head <- arbitrary[T]
      tail <- arbitrary[Vector[T]]
    } yield NonEmptySeq(head, tail)
  }
}
