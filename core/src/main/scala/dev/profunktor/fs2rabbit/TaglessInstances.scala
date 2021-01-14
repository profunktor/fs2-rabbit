/*
 * Copyright 2017-2020 ProfunKtor
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

object TaglessInstances {
//  type PublishListener[F[_]] = PublishReturn => F[Unit]

//  implicit def publishListenerContravariantK[F[_], G[_]]()
//  implicit val kleisliPublishListenerContravariantK: ContravariantK[Kleisli[*[_], PublishReturn, Unit]] =
//    ContravariantK[Kleisli[*[_], PublishReturn, Unit]]
//  implicit val publishListenerFunctorK: FunctorK[PublishListener] = new FunctorK[PublishListener] {
//    override def mapK[F[_], G[_]](af: PublishListener[F])(fk: F ~> G): PublishListener[G] = fk(af)
//  }
//  implicit val publishListenerContravariantK: ContravariantK[PublishListener] = new ContravariantK[PublishListener] {
//    override def contramapK[F[_], G[_]](af: PublishListener[F])(fk: G ~> F): PublishListener[G] = new PublishListener[G] {
//      override def apply(publishReturn: PublishReturn): G[Unit] = FunctorK[G, F]
//    }
//  }
//  implicit val rabbitClientFunctorK: FunctorK[RabbitClient] = Derive.functorK[RabbitClient]
}
