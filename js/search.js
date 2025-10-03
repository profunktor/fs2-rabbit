// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Code of Conduct",
      "url": "/CODE_OF_CONDUCT.html",
      "content": "Code of Conduct We are committed to providing a friendly, safe and welcoming environment for all, regardless of level of experience, gender, gender identity and expression, sexual orientation, disability, personal appearance, body size, race, ethnicity, age, religion, nationality, or other such characteristics. Everyone is expected to follow the Scala Code of Conduct when discussing the project on the available communication channels. If you are being harassed, please contact us immediately so that we can support you. ## Moderation For any questions, concerns, or moderation requests please contact a member of the project."
    } ,    
    {
      "title": "AckerConsumer",
      "url": "/consumers/ackerconsumer.html",
      "content": "AckerConsumer An AckerConsumer delegates the responsibility to acknowledge messages to the user. You are in total control of telling RabbitMQ when and if a message should be marked as consumed. Use this if you can’t lose any messages. import cats.effect.IO import dev.profunktor.fs2rabbit.model.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import fs2.Stream val queueName = QueueName(\"daQ\") def doSomething(consumer: Stream[IO, AmqpEnvelope[String]], acker: AckResult =&gt; IO[Unit]): IO[Unit] = IO.unit def program(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.createAckerConsumer[String](queueName).flatMap { case (acker, consumer) =&gt; doSomething(consumer, acker) } } When creating a consumer, you can tune the configuration by using BasicQos and ConsumerArgs. By default, the basic QOS is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. ConsumerArgs is by None by default since it’s optional. When defined, you can indicate consumerTag (default is “”), noLocal (default is false), exclusive (default is false) and args (default is an empty Map[String, *])."
    } ,    
    {
      "title": "AutoAckConsumer",
      "url": "/consumers/autoackconsumer.html",
      "content": "AutoAckConsumer An AutoAckConsumer acknowledges every consumed message automatically, so all you need to worry about is to process the message. Keep in mind that messages whose processing fails will still be acknowledged to RabbitMQ meaning that messages could get lost. import cats.effect.IO import cats.implicits.* import dev.profunktor.fs2rabbit.model.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import fs2.Stream val queueName = QueueName(\"daQ\") val doSomething: Stream[IO, AmqpEnvelope[String]] =&gt; IO[Unit] = consumer =&gt; IO.unit def program(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.createAutoAckConsumer[String](queueName).flatMap(doSomething) } When creating a consumer, you can tune the configuration by using BasicQos and ConsumerArgs. By default, the basicQOS is set to a prefetch size of 0, a prefetch count of 1 and global is set to false. The ConsumerArgs is None by default since it’s optional. When defined, you can indicate consumerTag (default is “”), noLocal (default is false), exclusive (default is false) and args (default is an empty Map[String, *])."
    } ,    
    {
      "title": "Client Metrics",
      "url": "/examples/client-metrics.html",
      "content": "Client Metrics RabbitMQ Java Client supports metrics collection via Dropwizard or Micrometer. At the moment of writing both providers are in the amqp-client 5.9.0. You can instantiate one as shown below. val registry = new MetricRegistry val dropwizardCollector = new StandardMetricsCollector(registry) Now it is ready to use. RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource Expose via JMX JMX provides a standard way to access performance metrics of an application. Dropwizard has a module to report metrics via JMX with metrics-jmx module. Please add it to the list of the dependencies. libraryDependencies += \"io.dropwizard.metrics\" % \"metrics-core\" % \"4.1.5\" libraryDependencies += \"io.dropwizard.metrics\" % \"metrics-jmx\" % \"4.1.5\" It provides JmxReporter for the metrics registry. It is a resource. It can be wrapped with acquire-release pattern for ease to use. object JmxReporterResource { def make[F[_]: Sync](registry: MetricRegistry): Resource[F, JmxReporter] = { val acquire = Sync[F].delay { val reporter = JmxReporter.forRegistry(registry).inDomain(\"com.rabbitmq.client.jmx\").build reporter.start() reporter } val close = (reporter: JmxReporter) =&gt; Sync[F].delay(reporter.close()).void Resource.make(acquire)(close) } } Let’s initialise the FS2 RabbitMQ client and AMQP channel with metrics. val resources = for { _ &lt;- JmxReporterResource.make[IO](registry) client &lt;- RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource channel &lt;- client.createConnection.flatMap(client.createChannel) } yield (channel, client) val program = resources.use { case (channel, client) =&gt; // Let's publish and consume and see the counters go up } The app is going to have now the following metrics under com.rabbitmq.client.jmx: Acknowledged messages Channels count Connections count Consumed messages Published messages Rejected messages Full Listing Let’s create an application that publishes and consumes messages with exposed JMX metrics on top of the Cats Effect. import java.nio.charset.StandardCharsets.UTF_8 import cats.data.{Kleisli, NonEmptyList} import cats.effect.{ExitCode, IO, IOApp, Resource, Sync} import cats.implicits.* import com.codahale.metrics.MetricRegistry import com.codahale.metrics.jmx.JmxReporter import com.rabbitmq.client.impl.StandardMetricsCollector import dev.profunktor.fs2rabbit.config.declaration.{DeclarationExchangeConfig, DeclarationQueueConfig} import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig} import dev.profunktor.fs2rabbit.effects.MessageEncoder import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.AckResult.Ack import dev.profunktor.fs2rabbit.model.ExchangeType.Topic import dev.profunktor.fs2rabbit.model.* import dev.profunktor.fs2rabbit.examples.putStrLn import fs2.* import scala.concurrent.duration.* object DropwizardMetricsDemo extends IOApp { private val config: Fs2RabbitConfig = Fs2RabbitConfig( virtualHost = \"/\", nodes = NonEmptyList.one(Fs2RabbitNodeConfig(host = \"127.0.0.1\", port = 5672)), username = Some(\"guest\"), password = Some(\"guest\"), ssl = false, connectionTimeout = 3.seconds, requeueOnNack = false, requeueOnReject = false, internalQueueSize = Some(500), requestedHeartbeat = 60.seconds, automaticRecovery = true, automaticTopologyRecovery = true, clientProvidedConnectionName = Some(\"app:rabbit\") ) private val queueName = QueueName(\"testQ\") private val exchangeName = ExchangeName(\"testEX\") private val routingKey = RoutingKey(\"testRK\") val simpleMessage = AmqpMessage(\"Hey!\", AmqpProperties.empty) implicit val stringMessageEncoder: MessageEncoder[IO, AmqpMessage[String]] = Kleisli[IO, AmqpMessage[String], AmqpMessage[Array[Byte]]](s =&gt; s.copy(payload = s.payload.getBytes(UTF_8)).pure[IO]) override def run(args: List[String]): IO[ExitCode] = { val registry = new MetricRegistry val dropwizardCollector = new StandardMetricsCollector(registry) val resources = for { _ &lt;- JmxReporterResource.make[IO](registry) client &lt;- RabbitClient.default[IO](config).withMetricsCollector(dropwizardCollector).resource channel &lt;- client.createConnection.flatMap(client.createChannel) } yield (channel, client) val program = resources.use { case (channel, client) =&gt; implicit val c = channel val setup = for { _ &lt;- client.declareQueue(DeclarationQueueConfig.default(queueName)) _ &lt;- client.declareExchange(DeclarationExchangeConfig.default(exchangeName, Topic)) _ &lt;- client.bindQueue(queueName, exchangeName, routingKey) ackerConsumer &lt;- client.createAckerConsumer[String](queueName) (acker, consumer) = ackerConsumer publisher &lt;- client.createPublisher[AmqpMessage[String]](exchangeName, routingKey) } yield (consumer, acker, publisher) Stream .eval(setup) .flatTap { case (consumer, acker, publisher) =&gt; Stream( Stream(simpleMessage).evalMap(publisher).repeat.metered(1.second), consumer.through(logPipe).evalMap(acker) ).parJoin(2) } .compile .drain } program.as(ExitCode.Success) } def logPipe[F[_]: Sync]: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =&gt; putStrLn(s\"Consumed: $amqpMsg\").as(Ack(amqpMsg.deliveryTag)) } } object JmxReporterResource { def make[F[_]: Sync](registry: MetricRegistry): Resource[F, JmxReporter] = { val acquire = Sync[F].delay { val reporter = JmxReporter.forRegistry(registry).inDomain(\"com.rabbitmq.client.jmx\").build reporter.start() reporter } val close = (reporter: JmxReporter) =&gt; Sync[F].delay(reporter.close()).void Resource.make(acquire)(close) } }"
    } ,    
    {
      "title": "Fs2 Rabbit Client",
      "url": "/client.html",
      "content": "Fs2 Rabbit Client RabbitClient is the main client that wraps the communication with RabbitMQ. The mandatory arguments are a Fs2RabbitConfig and a cats.effect.Dispatcher for running effects under the hood. Optionally, you can pass in a custom SSLContext and SaslConfig. An alternative constructor is provided for creating a cats.effect.Resource[F, Rabbit[F]] without directly handling the Dispatcher. import cats.effect.* import cats.effect.std.Dispatcher import com.rabbitmq.client.{DefaultSaslConfig, SaslConfig} import dev.profunktor.fs2rabbit.config.Fs2RabbitConfig import dev.profunktor.fs2rabbit.interpreter.RabbitClient import javax.net.ssl.SSLContext object RabbitClient { def apply[F[_]: Async]( config: Fs2RabbitConfig, dispatcher: Dispatcher[F], sslContext: Option[SSLContext] = None, saslConfig: SaslConfig = DefaultSaslConfig.PLAIN ): F[RabbitClient[F]] = ??? def resource[F[_]: Async]( config: Fs2RabbitConfig, sslContext: Option[SSLContext] = None, saslConfig: SaslConfig = DefaultSaslConfig.PLAIN ): Resource[F, RabbitClient[F]] = ??? } Its creation is effectful so you need to flatMap and pass it as an argument. For example: import cats.effect.* import cats.effect.std.Dispatcher import cats.syntax.functor.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import java.util.concurrent.Executors import scala.concurrent.duration.* object Program { def foo[F[_]](client: RabbitClient[F]): F[Unit] = ??? } class Demo extends IOApp { val config: Fs2RabbitConfig = Fs2RabbitConfig( virtualHost = \"/\", host = \"127.0.0.1\", username = Some(\"guest\"), password = Some(\"guest\"), port = 5672, ssl = false, connectionTimeout = 3.seconds, requeueOnNack = false, requeueOnReject = false, automaticTopologyRecovery = true, internalQueueSize = Some(500) ) override def run(args: List[String]): IO[ExitCode] = Dispatcher[IO].use { dispatcher =&gt; RabbitClient[IO](config, dispatcher).flatMap { client =&gt; Program.foo[IO](client).as(ExitCode.Success) } } }"
    } ,    
    {
      "title": "Configuration",
      "url": "/config.html",
      "content": "Configuration The main RabbitMQ configuration should be defined as Fs2RabbitConfig. You choose how to get the information, either from an application.conf file, from the environment or provided by an external system. A popular option that fits well the tech stack is Pure Config. import cats.data.NonEmptyList import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig} import scala.concurrent.duration.* val config = Fs2RabbitConfig( virtualHost = \"/\", nodes = NonEmptyList.one( Fs2RabbitNodeConfig( host = \"127.0.0.1\", port = 5672 ) ), username = Some(\"guest\"), password = Some(\"guest\"), ssl = false, connectionTimeout = 3.seconds, requeueOnNack = false, requeueOnReject = false, internalQueueSize = Some(500), requestedHeartbeat = 30.seconds, automaticRecovery = true, automaticTopologyRecovery = true, clientProvidedConnectionName = Some(\"app:rabbit\") ) The internalQueueSize indicates the size of the fs2’s bounded queue used internally to communicate with the AMQP Java driver. The automaticRecovery indicates whether the AMQP Java driver should try to recover broken connections. The requestedHeartbeat indicates heartbeat timeout. Should be non-zero and lower than 60."
    } ,    
    {
      "title": "Connection and Channel",
      "url": "/connection-channel.html",
      "content": "Connection and Channel These two are the primitive datatypes of the underlying Java AMQP client. What RabbitClient provides is the guarantee of acquiring, using and releasing both Connection and Channel in a safe way by using Resource. Even in the case of failure it is guaranteed that all the resources will be cleaned up. import cats.effect.{IO, Resource} import cats.implicits.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.AMQPChannel def program(R: RabbitClient[IO]): IO[Unit] = { val connChannel: Resource[IO, AMQPChannel] = R.createConnectionChannel connChannel.use { implicit channel =&gt; // Here create consumers, publishers, etc IO.unit } } Multiple channels per connection Creating a Connection is expensive so you might want to reuse it and create multiple Channels from it. There are two primitive operations that allow you to do this: createConnection: Resource[F, AMQPConnection] createChannel(conn: AMQPConnection): Resource[F, AMQPChannel] The operation createConnectionChannel is a convenient function defined in terms of these two primitives. def foo(R: RabbitClient[IO])(implicit channel: AMQPChannel): IO[Unit] = IO.unit def multChannels(R: RabbitClient[IO]): IO[Unit] = R.createConnection.use { conn =&gt; R.createChannel(conn).use { implicit channel =&gt; foo(R) } *&gt; R.createChannel(conn).use { implicit channel =&gt; foo(R) } }"
    } ,    
    {
      "title": "Exchanges",
      "url": "/exchanges.html",
      "content": "Exchanges Before getting into the Consumers section there are two things you need to know about Exchanges. Declaring a Exchange Declaring an Exchange will either create a new one or, in case an exchange of that name was already declared, returns a reference to an existing one. If the Exchange already exists, but has different properties (type, internal, …) the action will fail. import cats.effect.IO import cats.implicits.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.* val x1 = ExchangeName(\"x1\") val x2 = ExchangeName(\"x2\") def exchanges(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.declareExchange(x1, ExchangeType.Topic) *&gt; R.declareExchange(x2, ExchangeType.FanOut) } An Exchange can be declared passively, meaning that the Exchange is required to exist, whatever its properties. import cats.effect.IO import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.* val x = ExchangeName(\"x\") def moreExchanges(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.declareExchangePassive(x) } Binding Exchanges Two exchanges can be bound together by providing a RoutingKey and some extra arguments with ExchangeBindingArgs. def binding(R: RabbitClient[IO])(implicit channel: AMQPChannel) = R.bindExchange(x1, x2, RoutingKey(\"rk\"), ExchangeBindingArgs(Map.empty)) Read more about Exchanges and ExchangeType here."
    } ,    
    {
      "title": "Guide",
      "url": "/guide.html",
      "content": "Getting Started Configuration: The RabbitMQ configuration. Fs2 Rabbit Client: The RabbitMQ client. Connection and Channel: The two main primitives datatypes. Exchanges: Declaring and binding Exchanges. Queues: Declaring and binding Queues."
    } ,    
    {
      "title": "Examples",
      "url": "/examples/",
      "content": "Examples The source code for some of the examples can be found here. Simple Single AutoAckConsumer: Example of a single AutoAckConsumer, a Publisher and Json data manipulation. Single AckerConsumer: Example of a single AckerConsumer, a Publisher and Json data manipulation. Multiple Consumers: Two Consumers bound to queues with different RoutingKey, one Publisher. Client Metrics: Use RabbitMQ Java Client metrics collector to Dropwizard. Advanced Multiple Connections: Three different RabbitMQ Connections, two Consumers and one Publisher interacting with each other."
    } ,    
    {
      "title": "Publishers",
      "url": "/publishers/",
      "content": "Publishers Publishing are blocking actions in the underlying Java client so you need to pass in a cats.effect.Blocker when creating the RabbitClient client. Publisher: A simple message publisher. Publisher with Listener: A publisher with a listener for messages that can not be routed. Publishing Json: Publishing Json messages using the fs2-rabbit-circe module."
    } ,    
    {
      "title": "Consumers",
      "url": "/consumers/",
      "content": "Consumers There are two types of consumer: AutoAck and AckerConsumer. Each of them are parameterized on the effect type (eg. IO) and the data type they consume (the payload). EnvelopeDecoder When creating a consumer, either by using createAutoAckConsumer or createAckerConsumer, you’ll need an instance of EnvelopeDecoder[F, A] in scope. A default instance for Strings is provided by the library; it will use the contentEncoding of the message to determine the charset and fall back to UTF-8 if that is not present. If you wish to decode the envelope’s payload in a different format you’ll need to provide an implicit instance. Here’s the definition: type EnvelopeDecoder[F[_], A] = Kleisli[F, AmqpEnvelope[Array[Byte]], A] Kleisli[F, AmqpEnvelope[Array[Byte]], A] is a wrapper around a function AmqpEnvelope[Array[Byte]] =&gt; F[A]. You can for example write an EnvelopeDecoder for an array bytes thusly: implicit def bytesDecoder[F[_]: Applicative]: EnvelopeDecoder[F, Array[Byte]] = Kleisli(_.payload.pure[F]) You can write all your EnvelopeDecoder instances this way, but it’s usually easier to make use of existing instances. Kleisli forms a Monad, so you can use all the usual combinators like map: type WithThrowableError[F[_]] = ApplicativeError[F, Throwable] case class Foo(s: String) implicit def fooDecoder[F[_]: WithThrowableError]: EnvelopeDecoder[F, Foo] = EnvelopeDecoder[F, String].map(Foo.apply) Another useful combinator is flatMapF. For example a decoder for circe’s JSON type can be defined as follows: import io.circe.parser.* import io.circe.Json implicit def jsonDecoder[F[_]](implicit F: MonadError[F, Throwable]): EnvelopeDecoder[F, Json] = EnvelopeDecoder[F, String].flatMapF(s =&gt; F.fromEither(parse(s))) For more details, please refer to the the Kleisli documentation. The library comes with a number of EnvelopeDecoders predefined in object EnvelopeDecoder. These allow you to easily access both optional and non-optional header fields, the AmqpProperties object and the payload (as an Array[Byte]). Refer to the source code for details. AutoAckConsumer: A consumer that acknowledges message consumption automatically. AckerConsumer: A consumer that delegates the responsibility to acknowledge message consumption to the user. Consuming Json: Consuming Json messages using the fs2-rabbit-circe module."
    } ,    
    {
      "title": "Home",
      "url": "/",
      "content": "fs2-rabbit Stream-based library for RabbitMQ built-in on top of Fs2 and the RabbitMq Java Client. Dependencies From 5.4.0 onwards, the library is published only for Scala 3.x and 2.13.x For Scala 2.12.x use the latest &lt;= 5.3.x versions. Previous artifacts &lt;= 2.0.0-RC1 were published using the com.github.gvolpe group id (see migration guide) Add this to your build.sbt: libraryDependencies += \"dev.profunktor\" %% \"fs2-rabbit\" % Version And this one if you would like to have Json support: libraryDependencies += \"dev.profunktor\" %% \"fs2-rabbit-circe\" % Version Usage Guide Check the official guide for updated compiling examples. Adopters Company Description Cognotekt Microservice workflow management in Insuretech AI applications. ITV Internal microservices interaction. Klarna Microservice for Fintech services. Philips Lighting Internal microservices interaction. Descartes Kontainers Microservice workflow management - Logistics applications. Codacy Internal microservices interaction. Budgetbakers Internal microservices communication - Fintech Running tests locally Start a RabbitMQ instance using docker compose (recommended): &gt; docker compose up &gt; sbt +test Code of Conduct See the Code of Conduct LICENSE Licensed under the Apache License, Version 2.0 (the “License”); you may not use this project except in compliance with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0. Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an “AS IS” BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."
    } ,    
    {
      "title": "Publishing Json",
      "url": "/publishers/json.html",
      "content": "Publishing Json A stream-based Json Encoder that can be connected to a Publisher is provided by the extra dependency fs2-rabbit-circe. Implicit encoders for your classes must be on scope. You can use Circe’s codec auto derivation for example: import cats.effect.IO import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder import dev.profunktor.fs2rabbit.model.* import fs2.Stream import io.circe.generic.auto.* case class Address(number: Int, streetName: String) case class Person(id: Long, name: String, address: Address) object ioEncoder extends Fs2JsonEncoder def program(publisher: AmqpMessage[String] =&gt; IO[Unit]) = { import ioEncoder.* val message = AmqpMessage(Person(1L, \"Sherlock\", Address(212, \"Baker St\")), AmqpProperties.empty) Stream(message).covary[IO].map(jsonEncode[Person]).evalMap(publisher) } If you need to modify the output format, you can pass your own io.circe.Printer to the constructor of Fs2JsonEncoder (defaults to Printer.noSpaces)."
    } ,    
    {
      "title": "Consuming Json",
      "url": "/consumers/json.html",
      "content": "Json message Consuming A stream-based Json Decoder that can be connected to a stream of AmqpEnvelope is provided by the extra dependency fs2-rabbit-circe. Implicit decoders for your classes must be on scope. You can use Circe’s codec auto derivation for example: import cats.effect.IO import dev.profunktor.fs2rabbit.json.Fs2JsonDecoder import dev.profunktor.fs2rabbit.model.AckResult.* import dev.profunktor.fs2rabbit.model.* import io.circe.* import io.circe.generic.auto.* import fs2.* case class Address(number: Int, streetName: String) case class Person(id: Long, name: String, address: Address) object ioDecoder extends Fs2JsonDecoder def program(consumer: Stream[IO, AmqpEnvelope[String]], acker: AckResult =&gt; IO[Unit], errorSink: Pipe[IO, Error, Unit], processorSink: Pipe[IO, (Person, DeliveryTag), Unit]) = { import ioDecoder._ consumer.map(jsonDecode[Person]).flatMap { case (Left(error), tag) =&gt; (Stream.eval(IO(error)).through(errorSink)).as(NAck(tag)).evalMap(acker) case (Right(msg), tag) =&gt; Stream.eval(IO((msg, tag))).through(processorSink) } }"
    } ,      
    {
      "title": "Publisher with Listener",
      "url": "/publishers/publisher-with-listener.html",
      "content": "Publisher with Listener It is possible to add a listener when creating a publisher to handle messages that cannot be routed. The AMQP protocol defines two different bits that can be set when publishing a message: mandatory and immediate. You can read more about it in the AMQP reference. However, RabbitMQ only supports the mandatory bit in version 3.x so we don’t support the immediate bit either. Bit Mandatory This flag tells the server how to react if the message cannot be routed to a queue. If this flag is set, the server will return an unroutable message with a Return method. If this flag is zero, the server silently drops the message. The server SHOULD implement the mandatory flag. Creating a Publisher with Listener It is simply created by specifying ExchangeName, RoutingKey, PublishingFlag and a listener, i.e. a function from PublishReturn to F[Unit]: import cats.effect._ import dev.profunktor.fs2rabbit.model.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient val exchangeName = ExchangeName(\"testEX\") val routingKey = RoutingKey(\"testRK\") val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true) val publishingListener: PublishReturn =&gt; IO[Unit] = pr =&gt; IO(println(s\"Publish listener: $pr\")) def doSomething(publisher: String =&gt; IO[Unit]): IO[Unit] = IO.unit def program(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.createPublisherWithListener[String](exchangeName, routingKey, publishingFlag, publishingListener).flatMap(doSomething) } Publishing a simple message Once you have a Publisher you can start publishing messages by calling it: import cats.effect.Sync import dev.profunktor.fs2rabbit.model.* def publishSimpleMessage[F[_]: Sync](publisher: String =&gt; F[Unit]): F[Unit] = { val message = \"Hello world!\" publisher(message) } NOTE: If the mandatory flag is set to true and there’s no queue bound to the target exchange the message will return to the assigned publishing listener."
    } ,    
    {
      "title": "Publisher",
      "url": "/publishers/publisher.html",
      "content": "Publisher A Publisher is simply created by specifying an ExchangeName and a RoutingKey: import cats.effect.* import dev.profunktor.fs2rabbit.model.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient val exchangeName = ExchangeName(\"testEX\") val routingKey = RoutingKey(\"testRK\") def doSomething(publisher: String =&gt; IO[Unit]): IO[Unit] = IO.unit def program(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.createPublisher[String](exchangeName, routingKey).flatMap(doSomething) } Publishing a simple message Once you have a Publisher you can start publishing messages by simpy calling it: import cats.effect.Sync import dev.profunktor.fs2rabbit.model.* def publishSimpleMessage[F[_]: Sync](publisher: String =&gt; F[Unit]): F[Unit] = publisher(\"Hello world!\")"
    } ,    
    {
      "title": "Queues",
      "url": "/queues.html",
      "content": "Queues Before getting into the Consumers section there are two things you need to know about Queues. Declaring a Queue Declaring a Queue will either create a new one or, in case a queue of that name was already declared, returns a reference to an existing one. import cats.effect.IO import cats.implicits.* import dev.profunktor.fs2rabbit.config.declaration.* import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.* val q1 = QueueName(\"q1\") val q2 = QueueName(\"q2\") def exchanges(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.declareQueue(DeclarationQueueConfig.default(q1)) *&gt; R.declareQueue(DeclarationQueueConfig.default(q2)) } Binding a Queue to an Exchange val x1 = ExchangeName(\"x1\") val rk1 = RoutingKey(\"rk1\") val rk2 = RoutingKey(\"rk2\") def binding(R: RabbitClient[IO])(implicit channel: AMQPChannel) = R.bindQueue(q1, x1, rk1) *&gt; R.bindQueue(q2, x1, rk2)"
    } ,    
    {
      "title": "Resiliency",
      "url": "/resiliency.html",
      "content": "Resiliency If you want your program to run forever with automatic error recovery you can choose to run your program in a loop that will restart every certain amount of specified time with an exponential backoff then ResilientStream is all you’re looking for. For a given Fs2 Rabbit program defined as Stream[F, Unit], a resilient app will look as follow: import cats.effect.IO import dev.profunktor.fs2rabbit.resiliency.ResilientStream import fs2.* import scala.concurrent.duration.* val program: Stream[IO, Unit] = Stream.eval(IO.unit) ResilientStream.run(program, 1.second) This program will run forever and in the case of failure it will be restarted after 1 second and then exponentially after 2 seconds, 4 seconds, 8 seconds, etc. For a program defined as F[Unit] see the equivalent ResilientStream.runF. See the examples to learn more!"
    } ,    
    {
      "title": "Single AckerConsumer",
      "url": "/examples/sample-acker.html",
      "content": "Single AckerConsumer Here we create a single AckerConsumer, a single Publisher and finally we publish two messages: a simple String message and a Json message by using the fs2-rabbit-circe extension. import java.nio.charset.StandardCharsets.UTF_8 import cats.data.Kleisli import cats.effect.* import cats.implicits.* import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig import dev.profunktor.fs2rabbit.effects.MessageEncoder import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder import dev.profunktor.fs2rabbit.model.AckResult.Ack import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{LongVal, StringVal} import dev.profunktor.fs2rabbit.model.* import fs2.{Pipe, Pure, Stream} class Flow[F[_]: Concurrent, A]( consumer: Stream[F, AmqpEnvelope[A]], acker: AckResult =&gt; F[Unit], logger: Pipe[F, AmqpEnvelope[A], AckResult], publisher: AmqpMessage[String] =&gt; F[Unit] ) { import io.circe.generic.auto._ case class Address(number: Int, streetName: String) case class Person(id: Long, name: String, address: Address) private val jsonEncoder = new Fs2JsonEncoder import jsonEncoder.jsonEncode val jsonPipe: Pipe[Pure, AmqpMessage[Person], AmqpMessage[String]] = _.map(jsonEncode[Person]) val simpleMessage = AmqpMessage(\"Hey!\", AmqpProperties(headers = Headers(\"demoId\" -&gt; LongVal(123), \"app\" -&gt; StringVal(\"fs2RabbitDemo\")))) val classMessage = AmqpMessage(Person(1L, \"Sherlock\", Address(212, \"Baker St\")), AmqpProperties.empty) val flow: Stream[F, Unit] = Stream( Stream(simpleMessage).covary[F].evalMap(publisher), Stream(classMessage).through(jsonPipe).covary[F].evalMap(publisher), consumer.through(logger).evalMap(acker) ).parJoin(3) } class AckerConsumerDemo[F[_]: Async](R: RabbitClient[F]) { private val queueName = QueueName(\"testQ\") private val exchangeName = ExchangeName(\"testEX\") private val routingKey = RoutingKey(\"testRK\") implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] = Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s =&gt; s.copy(payload = s.payload.getBytes(UTF_8)).pure[F]) def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =&gt; Sync[F].delay(println(s\"Consumed: $amqpMsg\")).as(Ack(amqpMsg.deliveryTag)) } val publishingFlag: PublishingFlag = PublishingFlag(mandatory = true) // Run when there's no consumer for the routing key specified by the publisher and the flag mandatory is true val publishingListener: PublishReturn =&gt; F[Unit] = pr =&gt; Sync[F].delay(println(s\"Publish listener: $pr\")) val program: F[Unit] = R.createConnectionChannel.use { implicit channel =&gt; for { _ &lt;- R.declareQueue(DeclarationQueueConfig.default(queueName)) _ &lt;- R.declareExchange(exchangeName, ExchangeType.Topic) _ &lt;- R.bindQueue(queueName, exchangeName, routingKey) publisher &lt;- R.createPublisherWithListener[AmqpMessage[String]](exchangeName, routingKey, publishingFlag, publishingListener) ackerConsumer &lt;- R.createAckerConsumer[String](queueName) (acker, consumer) = ackerConsumer result = new Flow[F, String](consumer, acker, logPipe, publisher).flow _ &lt;- result.compile.drain } yield () } } At the edge of out program we define our effect, cats.effect.IO in this case, and ask to evaluate the effects: import cats.data.NonEmptyList import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig} import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.resiliency.ResilientStream import java.util.concurrent.Executors import scala.concurrent.duration.DurationInt object IOAckerConsumer extends IOApp { private val config: Fs2RabbitConfig = Fs2RabbitConfig( virtualHost = \"/\", nodes = NonEmptyList.one( Fs2RabbitNodeConfig( host = \"127.0.0.1\", port = 5672 ) ), username = Some(\"guest\"), password = Some(\"guest\"), ssl = false, connectionTimeout = 3.seconds, requeueOnNack = false, requeueOnReject = false, internalQueueSize = Some(500), requestedHeartbeat = 60.seconds, automaticRecovery = true, automaticTopologyRecovery = true, clientProvidedConnectionName = Some(\"app:rabbit\") ) override def run(args: List[String]): IO[ExitCode] = RabbitClient.default[IO](config).resource.use { client =&gt; ResilientStream .runF(new AckerConsumerDemo[IO](client).program) .as(ExitCode.Success) } }"
    } ,    
    {
      "title": "Single AutoAckConsumer",
      "url": "/examples/sample-autoack.html",
      "content": "Single AutoAckConsumer Here we create a single AutoAckConsumer, a single Publisher and finally we publish two messages: a simple String message and a Json message by using the fs2-rabbit-circe extension. import java.nio.charset.StandardCharsets.UTF_8 import cats.data.Kleisli import cats.effect.* import cats.implicits.* import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig import dev.profunktor.fs2rabbit.effects.MessageEncoder import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.json.Fs2JsonEncoder import dev.profunktor.fs2rabbit.model.AckResult.Ack import dev.profunktor.fs2rabbit.model.AmqpFieldValue.{LongVal, StringVal} import dev.profunktor.fs2rabbit.model.* import fs2.* class AutoAckFlow[F[_]: Async, A]( consumer: Stream[F, AmqpEnvelope[A]], logger: Pipe[F, AmqpEnvelope[A], AckResult], publisher: AmqpMessage[String] =&gt; F[Unit] ) { import io.circe.generic.auto._ case class Address(number: Int, streetName: String) case class Person(id: Long, name: String, address: Address) private val jsonEncoder = new Fs2JsonEncoder import jsonEncoder.jsonEncode val jsonPipe: Pipe[Pure, AmqpMessage[Person], AmqpMessage[String]] = _.map(jsonEncode[Person]) val simpleMessage = AmqpMessage(\"Hey!\", AmqpProperties(headers = Headers(\"demoId\" -&gt; LongVal(123), \"app\" -&gt; StringVal(\"fs2RabbitDemo\")))) val classMessage = AmqpMessage(Person(1L, \"Sherlock\", Address(212, \"Baker St\")), AmqpProperties.empty) val flow: Stream[F, Unit] = Stream( Stream(simpleMessage).covary[F] evalMap publisher, Stream(classMessage).through(jsonPipe).covary[F] evalMap publisher, consumer.through(logger).evalMap(ack =&gt; Sync[F].delay(println(ack))) ).parJoin(3) } class AutoAckConsumerDemo[F[_]: Async](R: RabbitClient[F]) { private val queueName = QueueName(\"testQ\") private val exchangeName = ExchangeName(\"testEX\") private val routingKey = RoutingKey(\"testRK\") implicit val stringMessageEncoder: MessageEncoder[F, AmqpMessage[String]] = Kleisli[F, AmqpMessage[String], AmqpMessage[Array[Byte]]](s =&gt; s.copy(payload = s.payload.getBytes(UTF_8)).pure[F]) def logPipe: Pipe[F, AmqpEnvelope[String], AckResult] = _.evalMap { amqpMsg =&gt; Sync[F].delay(println(s\"Consumed: $amqpMsg\")).as(Ack(amqpMsg.deliveryTag)) } val program: F[Unit] = R.createConnectionChannel.use { implicit channel =&gt; for { _ &lt;- R.declareQueue(DeclarationQueueConfig.default(queueName)) _ &lt;- R.declareExchange(exchangeName, ExchangeType.Topic) _ &lt;- R.bindQueue(queueName, exchangeName, routingKey) publisher &lt;- R.createPublisher[AmqpMessage[String]](exchangeName, routingKey) consumer &lt;- R.createAutoAckConsumer[String](queueName) _ &lt;- new AutoAckFlow[F, String](consumer, logPipe, publisher).flow.compile.drain } yield () } } At the edge of out program we define our effect, monix.eval.Task in this case, and ask to evaluate the effects (MONIX UNAVAILABLE): //import cats.data.NonEmptyList //import dev.profunktor.fs2rabbit.config.{Fs2RabbitConfig, Fs2RabbitNodeConfig} //import dev.profunktor.fs2rabbit.interpreter.RabbitClient //import dev.profunktor.fs2rabbit.resiliency.ResilientStream //import monix.eval.{Task, TaskApp} //import java.util.concurrent.Executors // //object MonixAutoAckConsumer extends TaskApp { // // private val config: Fs2RabbitConfig = Fs2RabbitConfig( // virtualHost = \"/\", // nodes = NonEmptyList.one( // Fs2RabbitNodeConfig( // host = \"127.0.0.1\", // port = 5672 // ) // ), // username = Some(\"guest\"), // password = Some(\"guest\"), // ssl = false, // connectionTimeout = 3, // requeueOnNack = false, // requeueOnReject = false, // internalQueueSize = Some(500), // requestedHeartbeat = 60, // automaticTopologyRecovery = true, // automaticRecovery = true // ) // // val blockerResource = // Resource // .make(Task(Executors.newCachedThreadPool()))(es =&gt; Task(es.shutdown())) // .map(Blocker.liftExecutorService) // // override def run(args: List[String]): Task[ExitCode] = // blockerResource.use { blocker =&gt; // RabbitClient[Task](config, blocker).flatMap { client =&gt; // ResilientStream // .runF(new AutoAckConsumerDemo[Task](client).program) // .as(ExitCode.Success) // } // } // //}"
    } ,    
    {
      "title": "Multiple Connections",
      "url": "/examples/sample-mult-connections.html",
      "content": "Multiple Connections This advanced case presents the challenge of having multiple (3) RabbitMQ Connections interacting with each other. We start by defining three different programs representing each connection, namely p1, p2 and p3 respectively. p1 defines a single Consumer for Connection 1, namely c1. p2 defines a single Consumer for Connection 2, namely c2. p3 defines a single Publisher for Connection 3. We will be consuming messages from c1 and c2, and publishing the result to p3 concurrently. Thanks to fs2 this becomes such a simple case: import cats.effect.* import cats.implicits.* import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.* import fs2.* val q1 = QueueName(\"q1\") val ex = ExchangeName(\"testEX\") val rk = RoutingKey(\"RKA\") Here’s our program p1 creating a Consumer representing the first Connection: def p1(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; R.declareExchange(ex, ExchangeType.Topic) *&gt; R.declareQueue(DeclarationQueueConfig.default(q1)) *&gt; R.bindQueue(q1, ex, rk) *&gt; R.createAutoAckConsumer[String](q1) } Here’s our program p2 creating a Consumer representing the second Connection: def p2(R: RabbitClient[IO]) = R.createConnectionChannel use { implicit channel =&gt; R.declareExchange(ex, ExchangeType.Topic) *&gt; R.declareQueue(DeclarationQueueConfig.default(q1)) *&gt; R.bindQueue(q1, ex, rk) *&gt; R.createAutoAckConsumer[String](q1) } Here’s our program p3 creating a Publisher representing the third Connection: def p3(R: RabbitClient[IO]) = R.createConnectionChannel use { implicit channel =&gt; R.declareExchange(ex, ExchangeType.Topic) *&gt; R.createPublisher(ex, rk) } And finally we compose all the three programs together: val pipe: Pipe[IO, AmqpEnvelope[String], String] = _.map(_.payload) def program(c: RabbitClient[IO]) = (p1(c), p2(c), p3(c)).mapN { case (c1, c2, pb) =&gt; (c1.through(pipe).evalMap(pb)).concurrently(c2.through(pipe).evalMap(pb)).compile.drain }"
    } ,    
    {
      "title": "Multiple Consumers",
      "url": "/examples/sample-mult-consumers.html",
      "content": "Multiple Consumers Given two Consumers bound to queues with different RoutingKeys RKA and RKB and a single Publisher bound to a single RoutingKey named RKA we will be publishing messages to both queues but expecting to only consume messages published to the RKA. The second consumer bound to RKB will not receive any messages: import cats.effect.* import dev.profunktor.fs2rabbit.config.declaration.DeclarationQueueConfig import dev.profunktor.fs2rabbit.interpreter.RabbitClient import dev.profunktor.fs2rabbit.model.* import fs2.* val q1 = QueueName(\"q1\") val q2 = QueueName(\"q2\") val ex = ExchangeName(\"testEX\") val rka = RoutingKey(\"RKA\") val rkb = RoutingKey(\"RKB\") val msg = Stream(\"Hey!\").covary[IO] def multipleConsumers(c1: Stream[IO, AmqpEnvelope[String]], c2: Stream[IO, AmqpEnvelope[String]], p: String =&gt; IO[Unit]) = { Stream( msg evalMap p, c1.through(_.evalMap(m =&gt; IO(println(s\"Consumer #1 &gt;&gt; $m\")))), c2.through(_.evalMap(m =&gt; IO(println(s\"Consumer #2 &gt;&gt; $m\")))) ).parJoin(3) } def program(R: RabbitClient[IO]) = R.createConnectionChannel.use { implicit channel =&gt; for { _ &lt;- R.declareExchange(ex, ExchangeType.Topic) _ &lt;- R.declareQueue(DeclarationQueueConfig.default(q1)) _ &lt;- R.declareQueue(DeclarationQueueConfig.default(q2)) _ &lt;- R.bindQueue(q1, ex, rka) _ &lt;- R.bindQueue(q2, ex, rkb) c1 &lt;- R.createAutoAckConsumer[String](q1) c2 &lt;- R.createAutoAckConsumer[String](q2) p &lt;- R.createPublisher[String](ex, rka) _ &lt;- multipleConsumers(c1, c2, p).compile.drain } yield () } If we run this program, we should only see a message Consumer #1 &gt;&gt; Hey! meaning that only the consumer bound to the RKA routing key got the message."
    } ,      
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
