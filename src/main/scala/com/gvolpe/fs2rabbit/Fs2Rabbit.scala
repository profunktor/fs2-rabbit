package com.gvolpe.fs2rabbit

import com.gvolpe.fs2rabbit.Fs2Utils._
import com.gvolpe.fs2rabbit.model._
import com.rabbitmq.client._
import fs2.async.mutable
import fs2.{Sink, Strategy, Stream, Task}

object Fs2Rabbit {

  // Connection and Channel
  private[Fs2Rabbit] val factory = {
    val f = new ConnectionFactory()
    f.setHost("localhost")
    f
  }

  private[Fs2Rabbit] def createChannel(queue: QueueName): Channel = {
    val conn    = factory.newConnection
    val channel = conn.createChannel
    channel.queueDeclare(queue, false, false, false, null)
    channel
  }

  // Consumer
  private[Fs2Rabbit] def defaultConsumer(channel: Channel,
                                         Q: mutable.Queue[Task, AmqpMessage]): Consumer = new DefaultConsumer(channel) {

    override def handleDelivery(consumerTag: String,
                                envelope: Envelope,
                                properties: AMQP.BasicProperties,
                                body: Array[Byte]): Unit = {
      val msg = new String(body, "UTF-8")
      val tag = envelope.getDeliveryTag
      Q.enqueue1(AmqpMessage(tag, msg)).unsafeRun()
    }

  }

  private[Fs2Rabbit] def createAcker(channel: Channel): Sink[Task, AckResult] =
    liftSink[AckResult] {
      case Ack(tag)   => Task.delay(channel.basicAck(tag, false))
      case NAck(tag)  => Task.delay(channel.basicNack(tag, false, true))
    }

  private[Fs2Rabbit] def createConsumer(queueName: QueueName,
                                        channel: Channel,
                                        autoAck: Boolean)
                                        (implicit S: Strategy): Stream[Task, AmqpMessage] =
    for {
      daQ       <- Stream.eval(fs2.async.boundedQueue[Task, AmqpMessage](100))
      dc        = defaultConsumer(channel, daQ)
      _         <- async(channel.basicConsume(queueName, autoAck, dc))
      consumer  <- daQ.dequeue
    } yield consumer

  // Public methods
  def createAckerConsumer(queueName: QueueName)(implicit S: Strategy): AckerConsumer = {
    val channel  = createChannel(queueName)
    channel.basicQos(1)
    val consumer = createConsumer(queueName, channel, autoAck = false)
    (consumer, createAcker(channel))
  }

  def createAutoAckConsumer(queueName: QueueName)(implicit S: Strategy): Stream[Task, AmqpMessage] = {
    val channel  = createChannel(queueName)
    createConsumer(queueName, channel, autoAck = true)
  }

  def createPublisher(exchangeName: ExchangeName,
                      queue: QueueName,
                      routingKey: RoutingKey)
                      (implicit S: Strategy): Sink[Task, String] = { streamMsg =>
    val channel = createChannel(queue)
    for {
      msg <- streamMsg
      _   <- async(channel.basicPublish(exchangeName, queue, null, msg.getBytes("UTF-8")))
    } yield ()
  }


}
