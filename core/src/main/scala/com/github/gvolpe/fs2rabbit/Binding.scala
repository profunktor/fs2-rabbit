package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.Binding._
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.collection.JavaConverters._
import scala.language.higherKinds

trait Binding {

  /**
    * Binds a queue to an exchange, with extra arguments.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param queueName the name of the queue
    * @param exchangeName the name of the exchange
    * @param routingKey the routing key to use for the binding
    *
    * @return an effectful [[fs2.Stream]] of type [[Queue.BindOk]]
    * */
  def bindQueue[F[_] : Sync](channel: Channel,
                             queueName: QueueName,
                             exchangeName: ExchangeName,
                             routingKey: RoutingKey): Stream[F, Queue.BindOk] =
    asyncF[F, Queue.BindOk] {
      channel.queueBind(queueName.name, exchangeName.name, routingKey.name)
    }

  /**
    * Binds a queue to an exchange with the given arguments.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param queueName the name of the queue
    * @param exchangeName the name of the exchange
    * @param routingKey the routing key to use for the binding
    * @param args other properties (binding parameters)
    *
    * @return an effectful [[fs2.Stream]] of type [[Queue.BindOk]]
    * */
  def bindQueue[F[_] : Sync](channel: Channel,
                             queueName: QueueName,
                             exchangeName: ExchangeName,
                             routingKey: RoutingKey,
                             args: QueueBindingArgs): Stream[F, Queue.BindOk] =
    asyncF[F, Queue.BindOk] {
      channel.queueBind(queueName.name, exchangeName.name, routingKey.name, args.value.asJava)
    }

  /**
    * Binds a queue to an exchange with the given arguments but sets nowait parameter to true and returns
    * nothing (as there will be no response from the server).
    *
    * @param channel the channel where the exchange is going to be declared
    * @param queueName the name of the queue
    * @param exchangeName the name of the exchange
    * @param routingKey the routing key to use for the binding
    * @param args other properties (binding parameters)
    *
    * @return an effectful [[fs2.Stream]]
    * */
  def bindQueueNoWait[F[_] : Sync](channel: Channel,
                                   queueName: QueueName,
                                   exchangeName: ExchangeName,
                                   routingKey: RoutingKey,
                                   args: QueueBindingArgs): Stream[F, Unit] =
    asyncF[F, Unit] {
      channel.queueBindNoWait(queueName.name, exchangeName.name, routingKey.name, args.value.asJava)
    }

  /**
    * Unbinds a queue from an exchange with the given arguments.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param queueName the name of the queue
    * @param exchangeName the name of the exchange
    * @param routingKey the routing key to use for the binding
    *
    * @return an effectful [[fs2.Stream]] of type [[Queue.BindOk]]
    * */
  def unbindQueue[F[_] : Sync](channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey): Stream[F, Queue.UnbindOk] =
    asyncF[F, Queue.UnbindOk] {
      channel.queueUnbind(queueName.name, exchangeName.name, routingKey.name)
    }

  /**
    * Binds an exchange to an exchange.
    *
    * @param channel the channel used to create a binding
    * @param destination the destination exchange
    * @param source the source exchange
    * @param routingKey  the routing key to use for the binding
    * @param args other properties
    *
    * @return an effectful [[fs2.Stream]] of type [[Exchange.BindOk]]
    * */
  def bindExchange[F[_]: Sync](channel: Channel,
                               destination: ExchangeName,
                               source: ExchangeName,
                               routingKey: RoutingKey,
                               args: ExchangeBindingArgs): Stream[F, Exchange.BindOk] =
    asyncF[F, Exchange.BindOk]{
      channel.exchangeBind(destination.name, source.name, routingKey.name, args.value.asJava)
    }

}

object Binding {
  case class QueueBindingArgs(value: Map[String, AnyRef])
  case class ExchangeBindingArgs(value: Map[String, AnyRef])
}