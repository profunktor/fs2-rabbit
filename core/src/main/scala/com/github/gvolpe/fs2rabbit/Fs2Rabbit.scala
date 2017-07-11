package com.github.gvolpe.fs2rabbit

import cats.effect.{Effect, IO}
import com.github.gvolpe.fs2rabbit.Fs2Utils._
import com.github.gvolpe.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitConfigManager}
import model._
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
trait Fs2Rabbit extends Declarations with Binding with Deletions {
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
                                     routingKey: RoutingKey): StreamPublisher[F] = { streamMsg =>
    for {
      msg   <- streamMsg
      _     <- asyncF[F, Unit] {
                 channel.basicPublish(exchangeName.name, routingKey.name, msg.properties.asBasicProps, msg.payload.getBytes("UTF-8"))
               }
    } yield ()
  }

}
