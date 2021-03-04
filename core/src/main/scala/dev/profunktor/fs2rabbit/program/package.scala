package dev.profunktor.fs2rabbit

import cats.{Functor, ~>}
import cats.implicits._
import dev.profunktor.fs2rabbit.arguments.Arguments
import dev.profunktor.fs2rabbit.effects.{EnvelopeDecoder, MessageEncoder}
import dev.profunktor.fs2rabbit.model._
import fs2.Stream

package object program {
  class AckConsumingProgramOps[F[_]](val prog: AckConsumingProgram[F]) extends AnyVal {
    def imapK[G[_]](fk: F ~> G)(gk: G ~> F)(implicit F: Functor[F]): AckConsumingProgram[G] =
      new AckConsumingProgram[G] {
        def createConsumer[A](
            queueName: QueueName,
            channel: AMQPChannel,
            basicQos: BasicQos,
            autoAck: Boolean,
            noLocal: Boolean,
            exclusive: Boolean,
            consumerTag: ConsumerTag,
            args: Arguments
        )(implicit decoder: EnvelopeDecoder[G, A]): G[Stream[G, AmqpEnvelope[A]]] =
          fk(
            prog
              .createConsumer[A](queueName, channel, basicQos, autoAck, noLocal, exclusive, consumerTag, args)(
                decoder.mapK(gk)
              )
              .map(_.translate(fk))
          )

        def createAcker(channel: AMQPChannel): G[AckResult => G[Unit]] =
          fk(prog.createAcker(channel).map(_.andThen(fk.apply)))

        def createAckerConsumer[A](
            channel: AMQPChannel,
            queueName: QueueName,
            basicQos: BasicQos,
            consumerArgs: Option[ConsumerArgs]
        )(implicit decoder: EnvelopeDecoder[G, A]): G[(AckResult => G[Unit], Stream[G, AmqpEnvelope[A]])] =
          fk(
            prog
              .createAckerConsumer[A](channel, queueName, basicQos, consumerArgs)(
                decoder.mapK(gk)
              )
              .map { case (acker, stream) =>
                (acker.andThen(fk.apply), stream.translate(fk))
              }
          )

        def createAutoAckConsumer[A](
            channel: AMQPChannel,
            queueName: QueueName,
            basicQos: BasicQos,
            consumerArgs: Option[ConsumerArgs]
        )(implicit decoder: EnvelopeDecoder[G, A]): G[Stream[G, AmqpEnvelope[A]]] =
          fk(
            prog
              .createAutoAckConsumer[A](channel, queueName, basicQos, consumerArgs)(
                decoder.mapK(gk)
              )
              .map(_.translate(fk))
          )

        def basicCancel(channel: AMQPChannel, consumerTag: ConsumerTag): G[Unit] =
          fk(prog.basicCancel(channel, consumerTag))
      }
  }

  class PublishingProgramOps[F[_]](val prog: PublishingProgram[F]) extends AnyVal {
    def imapK[G[_]](fk: F ~> G)(gk: G ~> F)(implicit F: Functor[F]): PublishingProgram[G] = new PublishingProgram[G] {
      def createPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName, routingKey: RoutingKey)(implicit
          encoder: MessageEncoder[G, A]
      ): G[A => G[Unit]] =
        fk(
          prog.createPublisher[A](channel, exchangeName, routingKey)(encoder.mapK(gk)).map(_.andThen(fk.apply))
        )

      def createPublisherWithListener[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          routingKey: RoutingKey,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[A => G[Unit]] =
        fk(
          prog
            .createPublisherWithListener[A](channel, exchangeName, routingKey, flags, listener.andThen(gk.apply))(
              encoder.mapK(gk)
            )
            .map(_.andThen(fk.apply))
        )

      def createRoutingPublisher[A](channel: AMQPChannel, exchangeName: ExchangeName)(implicit
          encoder: MessageEncoder[G, A]
      ): G[RoutingKey => A => G[Unit]] =
        fk(
          prog.createRoutingPublisher[A](channel, exchangeName)(encoder.mapK(gk)).map {
            f => (routingKey: RoutingKey) => (a: A) =>
              fk(f(routingKey)(a))
          }
        )

      def createRoutingPublisherWithListener[A](
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[RoutingKey => A => G[Unit]] =
        fk(
          prog
            .createRoutingPublisherWithListener[A](channel, exchangeName, flags, listener.andThen(gk.apply))(
              encoder.mapK(gk)
            )
            .map { f => (routingKey: RoutingKey) => (a: A) =>
              fk(f(routingKey)(a))
            }
        )

      def createBasicPublisher[A](
          channel: AMQPChannel
      )(implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
        fk(prog.createBasicPublisher[A](channel)(encoder.mapK(gk)).map { f =>
          { case (e, r, a) =>
            fk(f(e, r, a))
          }
        })

      def createBasicPublisherWithListener[A](
          channel: AMQPChannel,
          flags: PublishingFlag,
          listener: PublishReturn => G[Unit]
      )(implicit encoder: MessageEncoder[G, A]): G[(ExchangeName, RoutingKey, A) => G[Unit]] =
        fk(
          prog
            .createBasicPublisherWithListener[A](channel, flags, listener.andThen(gk.apply))(encoder.mapK(gk))
            .map { f =>
              { case (e, r, a) =>
                fk(f(e, r, a))
              }
            }
        )

      def basicPublish(
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          routingKey: RoutingKey,
          msg: AmqpMessage[Array[Byte]]
      ): G[Unit] =
        fk(prog.basicPublish(channel, exchangeName, routingKey, msg))

      def basicPublishWithFlag(
          channel: AMQPChannel,
          exchangeName: ExchangeName,
          routingKey: RoutingKey,
          flag: PublishingFlag,
          msg: AmqpMessage[Array[Byte]]
      ): G[Unit] = fk(prog.basicPublishWithFlag(channel, exchangeName, routingKey, flag, msg))

      def addPublishingListener(channel: AMQPChannel, listener: PublishReturn => G[Unit]): G[Unit] =
        fk(prog.addPublishingListener(channel, listener.andThen(gk.apply)))

      def clearPublishingListeners(channel: AMQPChannel): G[Unit] =
        fk(prog.clearPublishingListeners(channel))
    }
  }
}
