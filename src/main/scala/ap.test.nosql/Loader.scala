package ap.loader.akka

import akka.actor._
import akka.util._
import akka.pattern.ask
import akka.routing.SmallestMailboxPool

import concurrent.Future
import concurrent.duration._
import util._
import collection.JavaConversions._

object Loader
{
  case class Doc(id:Int, s:String)
}
import Loader._

object Main extends App
{
  val nbPar = Option(System.getProperty("nbPar")).getOrElse("1").toInt
  val nbMsg = Option(System.getProperty("nbMsg")).getOrElse("1").toInt
  val payloadSize = Option(System.getProperty("payloadSize")).getOrElse("1").toInt
  val payload = "a"*payloadSize

  val system = ActorSystem("loader")
  val loader = system.actorOf(Props[ESLoader]
                      .withRouter(SmallestMailboxPool(nrOfInstances = nbPar)))

  import system.dispatcher
  implicit val timeout = Timeout(5 minutes) 

  // launch requests
  val results:Stream[Future[Try[_]]] = 
    (1 to nbMsg).toStream map { id =>
      (loader ? Doc(id, s"""{"i":$id, "d":"$payload"}""")).mapTo[Try[_]]
    }

  // count results
  Future.fold(results)((0,0)) { case ((fail,succ),res) => res match {

    case Failure(e) => 
      println(e)
      (fail +1, succ)
    
    case Success(_) => 
      (fail, succ + 1)

  }} onComplete {
    case t => t match {
        case Success((fail,succ)) => println(s"  $fail KO | $succ OK")
        case Failure(e) => e.printStackTrace(); //ask timeout or something like that
      }
      println("Done!"); System.exit(0)
  }

}

trait Logger
{
  def log(id:Int) = 
    if (id % 100 == 0) print(s"  $id \r")// ${" "*100}\r") else print(".")
}


////////////////////: FakeLoader loads nothing! ://////////////////////////

class FakeLoader extends Actor with Logger
{
  def receive = {
    case Doc(id, doc) => 
      log(id)//println(s"${self.path}: $id -> $doc")
      sender ! Try { 
        Thread.sleep(10)
        "ok"
      }

    case x => println(x)
  }
}

////////////////////: Elastic ://////////////////////////

object ESClient
{
  import org.elasticsearch.client.Client
  import org.elasticsearch.common.settings.ImmutableSettings
  import org.elasticsearch.client.transport.TransportClient
  import org.elasticsearch.common.transport.InetSocketTransportAddress

  val client = new TransportClient
  client.addTransportAddress(new InetSocketTransportAddress("localhost", 9300));
}
class ESLoader extends Actor with Logger
{
  import ESClient.client
  import org.elasticsearch.action.WriteConsistencyLevel

  def receive = {
    case Doc(id, doc) => 
      log(id)//println(s"${self.path}: $id -> $doc")
      sender ! Try { 
        assert(
          client.prepareUpdate("test", "test", id.toString)
                .setDoc(doc).setUpsert(doc)
                .setConsistencyLevel(WriteConsistencyLevel.ALL)
                .execute.get.isCreated
          ,true)
      }

    case x => println(x)
  }
}

////////////////////: Mongo ://////////////////////////

object MongoClient
{
  import com.mongodb.MongoClient
  import com.mongodb.ServerAddress
  import org.jongo.Jongo

  val client = new Jongo(
    new MongoClient(
      List(new ServerAddress("localhost"))
    ).getDB("test")).getCollection("test")
}
class MongoLoader extends Actor with Logger
{
  import MongoClient.client

  def receive = {
    case Doc(id, doc) => 
      log(id)//println(s"${self.path}: $id -> $doc")
      sender ! Try { 
        client.update("{_id:#}", id.toString).upsert.`with`(doc) 
      }

    case x => println(x)
  }
}

