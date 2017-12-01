import akka.NotUsed
import akka.actor._
import akka.persistence._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy, ThrottleMode}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Random


object Asdf extends App {
  val system = ActorSystem("asdf")
  implicit val materializer: ActorMaterializer = ActorMaterializer.create(system)

  val receiver = system.actorOf(Props[Receiver])
  val throttler =
    Source.actorRef(bufferSize = 100, OverflowStrategy.dropTail)
      .throttle(3, 1.second, 1, ThrottleMode.Shaping)
      .to(Sink.actorRef(receiver, NotUsed))
      .run()
  val persistor = system.actorOf(Props(new Persistor(throttler)))

  system.scheduler.schedule(0 seconds, 1 second) {
    persistor ! Num(Random.nextInt(10))
  }
}

case class Num(n: Int)

class Persistor(val receiver: ActorRef) extends PersistentActor with ActorLogging {

  override def persistenceId = "sample-id-1"

  var state: List[Int] = Nil

  val receiveRecover: Receive = {
    case num@Num(n) =>
      state = n :: state
      log.info(s"Persistor recovered: $num")
      receiver ! Num(n)
  }

  val receiveCommand: Receive = {
    case num@Num(n) =>
      persist(num) { _ =>
        state = n :: state
        log.info(s"Persistor got: $num")
        receiver ! num
      }
  }
}

class Receiver extends Actor with ActorLogging {
  override def receive: Receive = {
    case m => log.info(s"Receiver got: $m")
  }
}