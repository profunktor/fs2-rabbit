package com.github.gvolpe.fs2rabbit

import cats.effect.Sync
import com.github.gvolpe.fs2rabbit.Fs2Utils.asyncF
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.github.gvolpe.fs2rabbit.model.{ExchangeName, QueueName}
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client.Channel
import fs2.Stream

import scala.collection.JavaConverters._
import scala.language.higherKinds

trait Declarations {

  /**
    * Declares an exchange.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param exchangeName the name of the exchange
    * @param exchangeType the exchange type: Direct, FanOut, Headers, Topic.
    *
    * @return an effectful [[fs2.Stream]] of type [[Exchange.DeclareOk]]
    * */
  def declareExchange[F[_] : Sync](channel: Channel,
                                   exchangeName: ExchangeName,
                                   exchangeType: ExchangeType): Stream[F, Exchange.DeclareOk] =
    asyncF[F, Exchange.DeclareOk] {
      channel.exchangeDeclare(exchangeName.name, exchangeType.toString.toLowerCase)
    }

  /**
    * Declares a queue.
    *
    * @param channel the channel where the queue is going to be declared
    * @param queueName the name of the queue
    *
    * @return an effectful [[fs2.Stream]] of type [[Queue.DeclareOk]]
    * */
  def declareQueue[F[_] : Sync](channel: Channel, queueName: QueueName): Stream[F, Queue.DeclareOk] =
    asyncF[F, Queue.DeclareOk] {
      channel.queueDeclare(queueName.name, false, false, false, Map.empty[String, AnyRef].asJava)
    }

}

object Declarations {
  case class QueueDeclarationArgs(value: Map[String, AnyRef])
  case class ExchangeDeclarationArgs(value: Map[String, AnyRef])
}