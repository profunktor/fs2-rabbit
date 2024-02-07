/*
 * Copyright 2017-2024 ProfunKtor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.profunktor.fs2rabbit

import scala.annotation.implicitNotFound

import scala.jdk.CollectionConverters._

object arguments {

  /** [[SafeArg]] makes sure the arguments passed to any of the functions are compliant with the AMQP Protocol.
    *
    * This library only supports String, Boolean, Int, Long, Float, Short, BigDecimal, Date, Byte, List and Map.
    */
  type SafeArg   = Evidence[SafeArgument]
  type Arguments = Map[String, SafeArg]

  implicit def argumentConversion(arguments: Arguments): java.util.Map[String, Object] =
    arguments.map { case (k, v) => k -> v.ev.toObject(v.value) }.asJava

  sealed trait Evidence[F[_]] {
    type A
    val value: A
    val ev: F[A]
  }

  final case class MkEvidence[F[_], A1](value: A1)(implicit val ev: F[A1]) extends Evidence[F] { type A = A1 }

  implicit def anySafeArg[F[_], A: F](a: A): Evidence[F] = MkEvidence(a)

  @implicitNotFound("Only types supported by the AMQP protocol are allowed. Custom classes are not supported.")
  sealed trait SafeArgument[A] {
    type JavaType >: Null <: AnyRef
    private[fs2rabbit] def toJavaType(a: A): JavaType
    private[fs2rabbit] def toObject(a: A): Object = toJavaType(a)
  }

  object SafeArgument {
    private[fs2rabbit] def apply[A](implicit ev: SafeArgument[A]): SafeArgument[A]      = ev
    private[fs2rabbit] def instance[A, J >: Null <: AnyRef](f: A => J): SafeArgument[A] =
      new SafeArgument[A] {
        type JavaType = J
        def toJavaType(a: A): J = f(a)
      }

    implicit val stringInstance: SafeArgument[String]         = instance(identity)
    implicit val bigDecimalInstance: SafeArgument[BigDecimal] = instance(_.bigDecimal)
    implicit val intInstance: SafeArgument[Int]               = instance(Int.box)
    implicit val longInstance: SafeArgument[Long]             = instance(Long.box)
    implicit val doubleInstance: SafeArgument[Double]         = instance(Double.box)
    implicit val floatInstance: SafeArgument[Float]           = instance(Float.box)
    implicit val shortInstance: SafeArgument[Short]           = instance(Short.box)
    implicit val booleanInstance: SafeArgument[Boolean]       = instance(Boolean.box)
    implicit val byteInstance: SafeArgument[Byte]             = instance(Byte.box)
    implicit val dateInstance: SafeArgument[java.util.Date]   = instance(identity)

    implicit def listInstance[A: SafeArgument]: SafeArgument[List[A]]                   = {
      val _ = implicitly[SafeArgument[A]]
      instance(_.asJava)
    }
    implicit def mapInstance[K: SafeArgument, V: SafeArgument]: SafeArgument[Map[K, V]] = {
      val _ = (implicitly[SafeArgument[K]], implicitly[SafeArgument[V]])
      instance(_.asJava)
    }
  }

}
