package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitConfigManager}
import model.ExchangeType.ExchangeType
import model._
import com.rabbitmq.client.AMQP.{Exchange, Queue}
import com.rabbitmq.client._
import fs2.async.mutable
import fs2.{Pipe, Sink, Stream}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.higherKinds

/**
  * The default Fs2Rabbit stream-based client using the default [[Fs2RabbitConfig]]
  * */
object Fs2Rabbit extends Fs2Rabbit with UnderlyingAmqpClient {
  protected override val log = LoggerFactory.getLogger(getClass)
  protected override lazy val fs2RabbitConfig = Fs2RabbitConfigManager.config
}

trait UnderlyingAmqpClient {
  protected val fs2RabbitConfig: Fs2RabbitConfig

  protected lazy val factory: ConnectionFactory = createConnectionFactory(fs2RabbitConfig)

  protected def createConnectionFactory(config: Fs2RabbitConfig): ConnectionFactory = {
    val factory = new ConnectionFactory()
    factory.setHost(config.host)
    factory.setPort(config.port)
    factory.setVirtualHost(config.virtualHost)
    factory.setConnectionTimeout(config.connectionTimeout)
    factory
  }

  protected def defaultConsumer(channel: Channel,
                                Q: mutable.Queue[IO, Either[Throwable, AmqpEnvelope]]): Consumer = new DefaultConsumer(channel) {

    override def handleCancel(consumerTag: String): Unit = {
      Q.enqueue1(Left(new Exception(s"Queue might have been DELETED! $consumerTag"))).unsafeRunSync()
    }

    override def handleDelivery(consumerTag: String,
                                envelope: Envelope,
                                properties: AMQP.BasicProperties,
                                body: Array[Byte]): Unit = {
      val msg   = new String(body, "UTF-8")
      val tag   = envelope.getDeliveryTag
      val props = AmqpProperties.from(properties)
      Q.enqueue1(Right(AmqpEnvelope(tag, msg, props))).unsafeRunSync()
    }

  }

  protected def createAcker[F[_]](channel: Channel)
                                 (implicit F: Effect[F]): Sink[F, AckResult] =
    liftSink[F, AckResult] {
      case Ack(tag)   => F.delay(channel.basicAck(tag, false))
      case NAck(tag)  => F.delay(channel.basicNack(tag, false, fs2RabbitConfig.requeueOnNack))
    }

  protected def createConsumer[F[_]](queueName: QueueName,
                                     channel: Channel,
                                     autoAck: Boolean)
                                    (implicit F: Effect[F], ec: ExecutionContext): StreamConsumer[F] = {
    val daQ = fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](100).unsafeRunSync()
    val dc  = defaultConsumer(channel, daQ)
    for {
      _         <- asyncF[F, String](channel.basicConsume(queueName.name, autoAck, dc))
      consumer  <- Stream.repeatEval(daQ.dequeue1.to[F]) through resilientConsumer
    } yield consumer
  }

  protected def acquireConnection[F[_]](implicit F: Effect[F]): F[(Connection, Channel)] =
    F.delay {
      val conn    = factory.newConnection
      val channel = conn.createChannel
      (conn, channel)
    }

  protected def resilientConsumer[F[_]](implicit F: Effect[F]): Pipe[F, Either[Throwable, AmqpEnvelope], AmqpEnvelope] =
    streamMsg =>
      streamMsg.flatMap {
        case Left(err)  => Stream.fail(err)
        case Right(env) => asyncF[F, AmqpEnvelope](env)
      }

}

/**
  * The core of the library that wraps the AMQP Java Client and gives you a Stream-based client.
  *
  * Any exception thrown by the underlying Java client will be represented as a Stream Failure.
  *
  * Acquisition of resources like [[Connection]] and [[Channel]] are performed in a safe way with
  * the help of [[fs2.Stream.bracket()]] that will free the resources when they're no longer needed.
  * */
trait Fs2Rabbit {
  self: UnderlyingAmqpClient =>

  protected def log: Logger

  /**
    * Creates a connection and a channel in a safe way using Stream.bracket.
    * In case of failure, the resources will be cleaned up properly.
    *
    * @return A tuple ([[Connection]], [[Channel]]) as a [[fs2.Stream]]
    * */
  def createConnectionChannel[F[_]]()(implicit F: Effect[F]): Stream[F, (Connection, Channel)] =
    Stream.bracket(acquireConnection)(
      cc => asyncF[F, (Connection, Channel)](cc),
      cc => F.delay {
        val (conn, channel) = cc
        log.info(s"Releasing connection: $conn and channel: ${channel.getChannelNumber} previously acquired.")
        if (channel.isOpen) channel.close()
        if (conn.isOpen) conn.close()
      }
    )

  /**
    * Creates a consumer and an acker to handle the acknowledgments with RabbitMQ.
    *
    * @param channel the channel where the consumer is going to be created
    * @param queueName the name of the queue
    *
    * @return A tuple ([[StreamAcker]], [[StreamConsumer]]) represented as [[StreamAckerConsumer]]
    * */
  def createAckerConsumer[F[_]](channel: Channel, queueName: QueueName)
                               (implicit F: Effect[F], ec: ExecutionContext): StreamAckerConsumer[F] = {
    channel.basicQos(1)
    val consumer = createConsumer(queueName, channel, autoAck = false)
    (createAcker(channel), consumer)
  }

  /**
    * Creates a consumer with an auto acknowledgment mechanism.
    *
    * @param channel the channel where the consumer is going to be created
    * @param queueName the name of the queue
    *
    * @return A [[StreamConsumer]] with data type represented as [[AmqpEnvelope]]
    * */
  def createAutoAckConsumer[F[_]](channel: Channel, queueName: QueueName)
                                 (implicit F: Effect[F], ec: ExecutionContext): StreamConsumer[F] = {
    createConsumer(queueName, channel, autoAck = true)
  }

  /**
    * Creates a simple publisher.
    *
    * @param channel the channel where the publisher is going to be created
    * @param exchangeName the name of the exchange
    * @param routingKey the name of the routing key
    *
    * @return A sink where messages of type [[AmqpMessage]] of [[String]] can be published represented as [[StreamPublisher]]
    * */
  def createPublisher[F[_]](channel: Channel,
                            exchangeName: ExchangeName,
                            routingKey: RoutingKey)
                           (implicit F: Effect[F], ec: ExecutionContext): StreamPublisher[F] = { streamMsg =>
    for {
      msg   <- streamMsg
      _     <- asyncF[F, Unit] {
        channel.basicPublish(exchangeName.name, routingKey.name, msg.properties.asBasicProps, msg.payload.getBytes("UTF-8"))
      }
    } yield ()
  }

  /**
    * Declares an exchange.
    *
    * @param channel the channel where the exchange is going to be declared
    * @param exchangeName the name of the exchange
    * @param exchangeType the exchange type: Direct, FanOut, Headers, Topic.
    *
    * @return an effectful [[fs2.Stream]] of type [[Exchange.DeclareOk]]
    * */
  def declareExchange[F[_]](channel: Channel, exchangeName: ExchangeName, exchangeType: ExchangeType)
                           (implicit F: Effect[F]): Stream[F, Exchange.DeclareOk] =
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
  def declareQueue[F[_]](channel: Channel, queueName: QueueName)
                        (implicit F: Effect[F]): Stream[F, Queue.DeclareOk] =
    asyncF[F, Queue.DeclareOk] {
      channel.queueDeclare(queueName.name, false, false, false, Map.empty[String, AnyRef].asJava)
    }

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
  def bindQueue[F[_]](channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey)
                     (implicit F: Effect[F]): Stream[F, Queue.BindOk] = {
    asyncF[F, Queue.BindOk] {
      channel.queueBind(queueName.name, exchangeName.name, routingKey.name)
    }
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
    * @return a an effectful [[fs2.Stream]] of type [[Queue.BindOk]]
    * */
  def bindQueue[F[_]](channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)
                     (implicit F: Effect[F]): Stream[F, Queue.BindOk] = {
    asyncF[F, Queue.BindOk] {
      channel.queueBind(queueName.name, exchangeName.name, routingKey.name, args.value.asJava)
    }
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
  def bindQueueNoWait[F[_]](channel: Channel, queueName: QueueName, exchangeName: ExchangeName, routingKey: RoutingKey, args: QueueBindingArgs)
                           (implicit F: Effect[F]): Stream[F, Unit] = {
    asyncF[F, Unit] {
      channel.queueBindNoWait(queueName.name, exchangeName.name, routingKey.name, args.value.asJava)
    }
  }

}
