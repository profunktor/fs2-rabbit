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

  protected def createAcker[F[_] : Effect](channel: Channel): Sink[F, AckResult] =
    liftSink[F, AckResult] {
      case Ack(tag)   => Effect[F].delay(channel.basicAck(tag, false))
      case NAck(tag)  => Effect[F].delay(channel.basicNack(tag, false, fs2RabbitConfig.requeueOnNack))
    }

  protected def createConsumer[F[_] : Effect](queueName: QueueName,
                                              channel: Channel,
                                              basicQos: BasicQos,
                                              autoAck: Boolean = false,
                                              noLocal: Boolean = false,
                                              exclusive: Boolean = false,
                                              consumerTag: String = "",
                                              args: Map[String, AnyRef] = Map.empty[String, AnyRef])
                                              (implicit ec: ExecutionContext): StreamConsumer[F] = {
    val daQ = fs2.async.boundedQueue[IO, Either[Throwable, AmqpEnvelope]](100).unsafeRunSync()
    val dc  = defaultConsumer(channel, daQ)
    for {
      _         <- asyncF[F, Unit](channel.basicQos(basicQos.prefetchSize, basicQos.prefetchCount, basicQos.global))
      _         <- asyncF[F, String](channel.basicConsume(queueName.name, autoAck, consumerTag, noLocal, exclusive, args.asJava, dc))
      consumer  <- Stream.repeatEval(daQ.dequeue1.to[F]) through resilientConsumer
    } yield consumer
  }

  protected def acquireConnection[F[_] : Effect]: F[(Connection, Channel)] =
    Effect[F].delay {
      val conn    = factory.newConnection
      val channel = conn.createChannel
      (conn, channel)
    }

  protected def resilientConsumer[F[_] : Effect]: Pipe[F, Either[Throwable, AmqpEnvelope], AmqpEnvelope] =
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
    * @return An effectful [[fs2.Stream]] of type [[Channel]]
    * */
  def createConnectionChannel[F[_] : Effect](): Stream[F, Channel] =
    Stream.bracket(acquireConnection)(
      cc => asyncF[F, Channel](cc._2),
      cc => Effect[F].delay {
        val (conn, channel) = cc
        log.info(s"Releasing connection: $conn previously acquired.")
        if (channel.isOpen) channel.close()
        if (conn.isOpen) conn.close()
      }
    )

  /**
    * Creates a consumer and an acker to handle the acknowledgments with RabbitMQ.
    *
    * @param channel the channel where the consumer is going to be created
    * @param queueName the name of the queue
    * @param basicQos the basic quality of service (default to prefetchSize = 0, prefetchCount = 1 and global = false)
    * @param consumerArgs consumer options: consumer tag (default is empty), exclusive (default is false), non-local (default is false) and extra arguments (default is empty).
    *
    * @return A tuple ([[StreamAcker]], [[StreamConsumer]]) represented as [[StreamAckerConsumer]]
    * */
  def createAckerConsumer[F[_] : Effect](channel: Channel,
                                         queueName: QueueName,
                                         basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                                         consumerArgs: Option[ConsumerArgs] = None)
                                        (implicit ec: ExecutionContext): StreamAckerConsumer[F] = {
    val consumer = consumerArgs.fold(createConsumer(queueName, channel, basicQos)) { args =>
      createConsumer(
        queueName = queueName,
        channel = channel,
        basicQos = basicQos,
        noLocal = args.noLocal,
        exclusive = args.exclusive,
        consumerTag = args.consumerTag,
        args = args.args)
    }
    (createAcker(channel), consumer)
  }

  /**
    * Creates a consumer with an auto acknowledgment mechanism.
    *
    * @param channel the channel where the consumer is going to be created
    * @param queueName the name of the queue
    * @param basicQos the basic quality of service (default to prefetchSize = 0, prefetchCount = 1 and global = false)
    * @param consumerArgs consumer options: consumer tag (default is empty), exclusive (default is false), non-local (default is false) and extra arguments (default is empty).
    *
    * @return A [[StreamConsumer]] with data type represented as [[AmqpEnvelope]]
    * */
  def createAutoAckConsumer[F[_] : Effect](channel: Channel,
                                           queueName: QueueName,
                                           basicQos: BasicQos = BasicQos(prefetchSize = 0, prefetchCount = 1),
                                           consumerArgs: Option[ConsumerArgs] = None)
                                 (implicit ec: ExecutionContext): StreamConsumer[F] = {
    consumerArgs.fold(createConsumer(queueName, channel, basicQos, autoAck = true)) { args =>
      createConsumer(
        queueName = queueName,
        channel = channel,
        basicQos = basicQos,
        autoAck = true,
        noLocal = args.noLocal,
        exclusive = args.exclusive,
        consumerTag = args.consumerTag,
        args = args.args
      )
    }
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
  def createPublisher[F[_] : Effect](channel: Channel,
                                     exchangeName: ExchangeName,
                                     routingKey: RoutingKey)
                                    (implicit ec: ExecutionContext): StreamPublisher[F] = { streamMsg =>
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
  def declareExchange[F[_] : Effect](channel: Channel,
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
  def declareQueue[F[_] : Effect](channel: Channel, queueName: QueueName): Stream[F, Queue.DeclareOk] =
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
  def bindQueue[F[_] : Effect](channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey): Stream[F, Queue.BindOk] = {
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
    * @return an effectful [[fs2.Stream]] of type [[Queue.BindOk]]
    * */
  def bindQueue[F[_] : Effect](channel: Channel,
                               queueName: QueueName,
                               exchangeName: ExchangeName,
                               routingKey: RoutingKey,
                               args: QueueBindingArgs): Stream[F, Queue.BindOk] = {
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
  def bindQueueNoWait[F[_] : Effect](channel: Channel,
                                     queueName: QueueName,
                                     exchangeName: ExchangeName,
                                     routingKey: RoutingKey,
                                     args: QueueBindingArgs): Stream[F, Unit] = {
    asyncF[F, Unit] {
      channel.queueBindNoWait(queueName.name, exchangeName.name, routingKey.name, args.value.asJava)
    }
  }

  /**
    * Delete a queue.
    *
    * @param channel the channel where the publisher is going to be created
    * @param queueName the name of the queue
    * @param ifUnused true if the queue should be deleted only if not in use
    * @param ifEmpty true if the queue should be deleted only if empty
    *
    * @return an effectful [[fs2.Stream]]
    * */
  def deleteQueue[F[_] : Effect](channel: Channel,
                                 queueName: QueueName,
                                 ifUnused: Boolean = true,
                                 ifEmpty: Boolean = true)
                                 (implicit ec: ExecutionContext): Stream[F, Queue.DeleteOk] = {
    asyncF[F, Queue.DeleteOk] {
      channel.queueDelete(queueName.name, ifUnused, ifEmpty)
    }
  }

  /**
    * Delete a queue without waiting for the response from the server.
    *
    * @param channel the channel where the publisher is going to be created
    * @param queueName the name of the queue
    * @param ifUnused true if the queue should be deleted only if not in use
    * @param ifEmpty true if the queue should be deleted only if empty
    *
    * @return an effectful [[fs2.Stream]]
    * */
  def deleteQueueNoWait[F[_] : Effect](channel: Channel,
                                       queueName: QueueName,
                                       ifUnused: Boolean = true,
                                       ifEmpty: Boolean = true)
                                       (implicit ec: ExecutionContext): Stream[F,Unit] = {
    asyncF[F, Unit] {
      channel.queueDeleteNoWait(queueName.name, ifUnused, ifEmpty)
    }
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
  def bindExchange[F[_]: Effect](channel: Channel,
                                 destination: ExchangeName,
                                 source: ExchangeName,
                                 routingKey: RoutingKey,
                                 args: ExchangeBindingArgs): Stream[F, Exchange.BindOk] = {
    asyncF[F, Exchange.BindOk]{
      channel.exchangeBind(destination.name, source.name, routingKey.name, args.value.asJava)
    }
  }

}
