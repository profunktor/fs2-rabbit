package com.github.gvolpe.fs2rabbit

import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.config.Fs2RabbitConfigManager
import model.ExchangeType.ExchangeType
import model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client._
import fs2.async.mutable
import fs2.{Pipe, Sink, Strategy, Stream, Task}

object Fs2Rabbit {

  // Connection and Channel
  private[Fs2Rabbit] val factory = createRabbitConnectionFactory

  private lazy val fs2RabbitConfig = Fs2RabbitConfigManager.config

  private[Fs2Rabbit] def createRabbitConnectionFactory: ConnectionFactory = {
    val factory = new ConnectionFactory()
    factory.setHost(fs2RabbitConfig.host)
    factory.setPort(fs2RabbitConfig.port)
    factory.setConnectionTimeout(fs2RabbitConfig.connectionTimeout)
    factory
  }

  // Consumer
  private[Fs2Rabbit] def defaultConsumer(channel: Channel,
                                         Q: mutable.Queue[Task, Either[Throwable, AmqpEnvelope]]): Consumer = new DefaultConsumer(channel) {

    override def handleCancel(consumerTag: String): Unit = {
      Q.enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag"))).unsafeRun()
    }

    override def handleDelivery(consumerTag: String,
                                envelope: Envelope,
                                properties: AMQP.BasicProperties,
                                body: Array[Byte]): Unit = {
      val msg   = new String(body, "UTF-8")
      val tag   = envelope.getDeliveryTag
      val props = AmqpProperties.from(properties)
      Q.enqueue1(Right(AmqpEnvelope(tag, msg, props))).unsafeRun()
    }

  }

  private[Fs2Rabbit] def createAcker(channel: Channel): Sink[Task, AckResult] =
    liftSink[AckResult] {
      case Ack(tag)   => Task.delay(channel.basicAck(tag, false))
      case NAck(tag)  => Task.delay(channel.basicNack(tag, false, fs2RabbitConfig.requeueOnNack))
    }

  private[Fs2Rabbit] def createConsumer(queueName: QueueName,
                                        channel: Channel,
                                        autoAck: Boolean)
                                        (implicit S: Strategy): StreamConsumer =
    for {
      daQ       <- Stream.eval(fs2.async.boundedQueue[Task, Either[Throwable, AmqpEnvelope]](100))
      dc        = defaultConsumer(channel, daQ)
      _         <- async(channel.basicConsume(queueName, autoAck, dc))
      consumer  <- daQ.dequeue through resilientConsumer
    } yield consumer

  private[Fs2Rabbit] def acquireConnection: Task[(Connection, Channel)] = Task.delay {
    val conn    = factory.newConnection
    val channel = conn.createChannel
    (conn, channel)
  }

  private[Fs2Rabbit] def resilientConsumer: Pipe[Task, Either[Throwable, AmqpEnvelope], AmqpEnvelope] = { streamMsg =>
    streamMsg.flatMap {
      case Left(err)  => Stream.fail(err)
      case Right(env) => async(env)
    }
  }

  // Public methods
  def createConnectionChannel(): Stream[Task, (Connection, Channel)] =
    Stream.bracket(acquireConnection)(
      cc => async(cc),
      cc => Task.delay {
        val (conn, channel) = cc
        if (channel.isOpen) channel.close()
        if (conn.isOpen) conn.close()
      }
    )

  def createAckerConsumer(channel: Channel, queueName: QueueName)(implicit S: Strategy): StreamAckerConsumer = {
    channel.basicQos(1)
    val consumer = createConsumer(queueName, channel, autoAck = false)
    (createAcker(channel), consumer)
  }

  def createAutoAckConsumer(channel: Channel, queueName: QueueName)(implicit S: Strategy): StreamConsumer = {
    createConsumer(queueName, channel, autoAck = true)
  }

  def createPublisher(channel: Channel,
                      exchangeName: ExchangeName,
                      routingKey: RoutingKey)
                      (implicit S: Strategy): StreamPublisher = { streamMsg =>
    for {
      msg   <- streamMsg
      _     <- async(channel.basicPublish(exchangeName, routingKey, msg.properties.asBasicProps, msg.payload.getBytes("UTF-8")))
    } yield ()
  }

  def declareExchange(channel: Channel, exchangeName: ExchangeName, exchangeType: ExchangeType): Stream[Task, Exchange.DeclareOk] =
    async {
      channel.exchangeDeclare(exchangeName, exchangeType.toString.toLowerCase)
    }

  def declareQueue(channel: Channel, queueName: QueueName): Stream[Task, Queue.DeclareOk] =
    async {
      channel.queueDeclare(queueName, false, false, false, null)
    }

}
