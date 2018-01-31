/*
 * Copyright 2017 Fs2 Rabbit
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

package com.github.gvolpe.fs2rabbit.interpreter

import java.util

import cats.effect.IO
import com.github.gvolpe.fs2rabbit.algebra.AMQPClient
import com.github.gvolpe.fs2rabbit.model
import com.github.gvolpe.fs2rabbit.model._
import com.github.gvolpe.fs2rabbit.model.ExchangeType.ExchangeType
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client._
import fs2.Stream
import fs2.async.mutable

class AMQPClientInMemory(internalQ: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]],
                         ackerQ: mutable.Queue[IO, AckResult]) extends AMQPClient[Stream[IO, ?]] {

  override def basicAck(channel: Channel, tag: model.DeliveryTag, multiple: Boolean): Stream[IO, Unit] = {
    Stream.eval(ackerQ.enqueue1(Ack(tag)))
  }
  override def basicNack(channel: Channel, tag: model.DeliveryTag, multiple: Boolean, requeue: Boolean): Stream[IO, Unit] = {
    Stream.eval(ackerQ.enqueue1(NAck(tag)))
  }
  override def basicQos(channel: Channel, basicQos: model.BasicQos): Stream[IO, Unit] = Stream.eval(IO.unit)

  override def basicConsume(channel: Channel, queueName: model.QueueName, autoAck: Boolean, consumerTag: String, noLocal: Boolean, exclusive: Boolean, args: Map[String, AnyRef]): Stream[IO, String] = {
    internalQ.dequeue.map {
      case Right(v) => v.payload
      case Left(e)  => e.toString
    }
  }
  override def basicPublish(channel: Channel, exchangeName: model.ExchangeName, routingKey: model.RoutingKey, msg: model.AmqpMessage[String]): Stream[IO, Unit] = {
    val envelope = AmqpEnvelope(DeliveryTag(22), msg.payload, msg.properties)
    Stream.eval(internalQ.enqueue1(Right(envelope)))
  }

  override def deleteQueue(channel: Channel, queueName: model.QueueName, ifUnused: Boolean, ifEmpty: Boolean): Stream[IO, Queue.DeleteOk] = Stream.eval(IO(TestQueueDeleteOk))
  override def deleteQueueNoWait(channel: Channel, queueName: model.QueueName, ifUnused: Boolean, ifEmpty: Boolean): Stream[IO, Unit] = Stream.eval(IO.unit)
  override def bindQueue(channel: Channel, queueName: model.QueueName, exchangeName: model.ExchangeName, routingKey: model.RoutingKey): Stream[IO, Queue.BindOk] = Stream.eval(IO(TestQueueBindOk))
  override def bindQueue(channel: Channel, queueName: model.QueueName, exchangeName: model.ExchangeName, routingKey: model.RoutingKey, args: model.QueueBindingArgs): Stream[IO, Queue.BindOk] = Stream.eval(IO(TestQueueBindOk))
  override def bindQueueNoWait(channel: Channel, queueName: model.QueueName, exchangeName: model.ExchangeName, routingKey: model.RoutingKey, args: model.QueueBindingArgs): Stream[IO, Unit] = Stream.eval(IO.unit)
  override def unbindQueue(channel: Channel, queueName: model.QueueName, exchangeName: model.ExchangeName, routingKey: model.RoutingKey): Stream[IO, Queue.UnbindOk] = Stream.eval(IO(TestQueueUnbindOk))
  override def bindExchange(channel: Channel, destination: model.ExchangeName, source: model.ExchangeName, routingKey: model.RoutingKey, args: model.ExchangeBindingArgs): Stream[IO, Exchange.BindOk] = Stream.eval(IO(TestExchangeBindOk))
  override def declareExchange(channel: Channel, exchangeName: model.ExchangeName, exchangeType: ExchangeType): Stream[IO, Exchange.DeclareOk] = Stream.eval(IO(TestExchangeDeclareOk))
  override def declareQueue(channel: Channel, queueName: model.QueueName): Stream[IO, Queue.DeclareOk] = Stream.eval(IO(TestQueueDeclareOk))
}

object TestQueueDeleteOk extends Queue.DeleteOk {
  override def getMessageCount = ???
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestQueueBindOk extends Queue.BindOk {
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestQueueUnbindOk extends Queue.UnbindOk {
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestExchangeBindOk extends Exchange.BindOk {
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestQueueDeclareOk extends Queue.DeclareOk {
  override def getMessageCount = ???
  override def getQueue = ???
  override def getConsumerCount = ???
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestExchangeDeclareOk extends Exchange.DeclareOk {
  override def protocolMethodName() = ???
  override def protocolMethodId() = ???
  override def protocolClassId() = ???
}

object TestChannel extends Channel {
  override def queueBindNoWait(queue: String, exchange: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def clearReturnListeners() = ???
  override def waitForConfirms() = ???
  override def waitForConfirms(timeout: Long) = ???
  @deprecated("","")
  override def addFlowListener(listener: FlowListener) = ???
  override def messageCount(queue: String) = ???
  override def getDefaultConsumer = ???
  override def addReturnListener(listener: ReturnListener) = ???
  override def clearFlowListeners() = ???
  override def queueDelete(queue: String) = ???
  override def queueDelete(queue: String, ifUnused: Boolean, ifEmpty: Boolean) = ???
  override def exchangeUnbindNoWait(destination: String, source: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def waitForConfirmsOrDie() = ???
  override def waitForConfirmsOrDie(timeout: Long) = ???
  override def exchangeDeclareNoWait(exchange: String, `type`: String, durable: Boolean, autoDelete: Boolean, internal: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def exchangeDeclareNoWait(exchange: String, `type`: BuiltinExchangeType, durable: Boolean, autoDelete: Boolean, internal: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def basicNack(deliveryTag: Long, multiple: Boolean, requeue: Boolean) = ???
  override def exchangeUnbind(destination: String, source: String, routingKey: String) = ???
  override def exchangeUnbind(destination: String, source: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def confirmSelect() = ???
  override def exchangeBindNoWait(destination: String, source: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def queuePurge(queue: String) = ???
  override def basicQos(prefetchSize: Int, prefetchCount: Int, global: Boolean) = ???
  override def basicQos(prefetchCount: Int, global: Boolean) = ???
  override def basicQos(prefetchCount: Int) = ???
  override def queueBind(queue: String, exchange: String, routingKey: String) = ???
  override def queueBind(queue: String, exchange: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def basicCancel(consumerTag: String) = ???
  override def exchangeBind(destination: String, source: String, routingKey: String) = ???
  override def exchangeBind(destination: String, source: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def txRollback() = ???
  override def queueDeclare() = ???
  override def queueDeclare(queue: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def abort() = ???
  override def abort(closeCode: Int, closeMessage: String) = ???
  override def addConfirmListener(listener: ConfirmListener) = ???
  override def flowBlocked() = ???
  override def asyncRpc(method: Method) = ???
  override def txCommit() = ???
  override def getNextPublishSeqNo = ???
  override def queueUnbind(queue: String, exchange: String, routingKey: String) = ???
  override def queueUnbind(queue: String, exchange: String, routingKey: String, arguments: util.Map[String, AnyRef]) = ???
  override def getConnection = ???
  override def exchangeDelete(exchange: String, ifUnused: Boolean) = ???
  override def exchangeDelete(exchange: String) = ???
  override def basicConsume(queue: String, callback: Consumer) = ???
  override def basicConsume(queue: String, autoAck: Boolean, callback: Consumer) = ???
  override def basicConsume(queue: String, autoAck: Boolean, arguments: util.Map[String, AnyRef], callback: Consumer) = ???
  override def basicConsume(queue: String, autoAck: Boolean, consumerTag: String, callback: Consumer) = ???
  override def basicConsume(queue: String, autoAck: Boolean, consumerTag: String, noLocal: Boolean, exclusive: Boolean, arguments: util.Map[String, AnyRef], callback: Consumer) = ???
  @deprecated("","")
  override def removeFlowListener(listener: FlowListener) = ???
  override def basicAck(deliveryTag: Long, multiple: Boolean) = ???
  override def exchangeDeclarePassive(name: String) = ???
  override def basicRecover() = ???
  override def basicRecover(requeue: Boolean) = ???
  override def queueDeclareNoWait(queue: String, durable: Boolean, exclusive: Boolean, autoDelete: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def exchangeDeclare(exchange: String, `type`: String) = ???
  override def exchangeDeclare(exchange: String, `type`: BuiltinExchangeType) = ???
  override def exchangeDeclare(exchange: String, `type`: String, durable: Boolean) = ???
  override def exchangeDeclare(exchange: String, `type`: BuiltinExchangeType, durable: Boolean) = ???
  override def exchangeDeclare(exchange: String, `type`: String, durable: Boolean, autoDelete: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def exchangeDeclare(exchange: String, `type`: BuiltinExchangeType, durable: Boolean, autoDelete: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def exchangeDeclare(exchange: String, `type`: String, durable: Boolean, autoDelete: Boolean, internal: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def exchangeDeclare(exchange: String, `type`: BuiltinExchangeType, durable: Boolean, autoDelete: Boolean, internal: Boolean, arguments: util.Map[String, AnyRef]) = ???
  override def basicReject(deliveryTag: Long, requeue: Boolean) = ???
  override def close() = ???
  override def close(closeCode: Int, closeMessage: String) = ???
  override def queueDeclarePassive(queue: String) = ???
  override def consumerCount(queue: String) = ???
  override def rpc(method: Method) = ???
  override def exchangeDeleteNoWait(exchange: String, ifUnused: Boolean) = ???
  override def clearConfirmListeners() = ???
  override def removeReturnListener(listener: ReturnListener) = ???
  override def basicGet(queue: String, autoAck: Boolean) = ???
  override def setDefaultConsumer(consumer: Consumer) = ???
  override def removeConfirmListener(listener: ConfirmListener) = ???
  override def queueDeleteNoWait(queue: String, ifUnused: Boolean, ifEmpty: Boolean) = ???
  override def txSelect() = ???
  override def basicPublish(exchange: String, routingKey: String, props: AMQP.BasicProperties, body: Array[Byte]) = ???
  override def basicPublish(exchange: String, routingKey: String, mandatory: Boolean, props: AMQP.BasicProperties, body: Array[Byte]) = ???
  override def basicPublish(exchange: String, routingKey: String, mandatory: Boolean, immediate: Boolean, props: AMQP.BasicProperties, body: Array[Byte]) = ???
  override def getChannelNumber = ???
  override def removeShutdownListener(listener: ShutdownListener) = ???
  override def notifyListeners() = ???
  override def addShutdownListener(listener: ShutdownListener) = ???
  override def getCloseReason = ???
  override def isOpen = ???
}
