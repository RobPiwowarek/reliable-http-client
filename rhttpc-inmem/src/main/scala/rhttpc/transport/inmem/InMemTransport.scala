package rhttpc.transport.inmem

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import akka.util.Timeout
import rhttpc.transport.{InboundQueueData, Publisher, _}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.FiniteDuration

private[inmem] class InMemTransport(transportActor: ActorRef) // TODO: stopping of transports / actors
                                   (createTimeout: FiniteDuration,
                                    stopConsumingTimeout: FiniteDuration,
                                    stopTimeout: FiniteDuration)
                                   (implicit system: ActorSystem) extends PubSubTransport with WithInstantPublisher with WithDelayedPublisher {

  import system.dispatcher

  override def publisher[PubMsg <: AnyRef](queueData: OutboundQueueData): Publisher[PubMsg] = {
    val queueActor = getOrCreateQueueActor(queueData.name)
    new InMemPublisher[PubMsg](queueActor)
  }

  override def subscriber[SubMsg: Manifest](queueData: InboundQueueData, consumer: ActorRef): Subscriber[SubMsg] = {
    val queueActor = getOrCreateQueueActor(queueData.name)
    new InMemSubscriber[SubMsg](queueActor, consumer, fullMessage = false)(stopConsumingTimeout)
  }

  override def fullMessageSubscriber[SubMsg: Manifest](queueData: InboundQueueData, consumer: ActorRef): Subscriber[SubMsg] = {
    val queueActor = getOrCreateQueueActor(queueData.name)
    new InMemSubscriber[SubMsg](queueActor, consumer, fullMessage = true)(stopConsumingTimeout)
  }

  private def getOrCreateQueueActor[SubMsg: Manifest](name: String): ActorRef = {
    implicit val timeout = Timeout(createTimeout)
    Await.result((transportActor ? GetOrCreateQueue(name)).mapTo[ActorRef], createTimeout)
  }

  override def stop(): Future[Unit] = gracefulStop(transportActor, stopTimeout).map(_ => Unit)
}

object InMemTransport {
  def apply(createTimeout: FiniteDuration = InMemDefaults.createTimeout,
            stopConsumingTimeout: FiniteDuration = InMemDefaults.stopConsumingTimeout,
            stopTimeout: FiniteDuration = InMemDefaults.stopTimeout)
           (implicit system: ActorSystem): PubSubTransport with WithInstantPublisher with WithDelayedPublisher = {
    val actor = system.actorOf(TransportActor.props(QueueActor.props))
    new InMemTransport(actor)(
      createTimeout = createTimeout,
      stopConsumingTimeout = stopConsumingTimeout,
      stopTimeout = stopTimeout)
  }
}