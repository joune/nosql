package ap.test.nosql

import akka.actor._
import scala.concurrent.Future

object Main extends App {

  implicit val akka = ActorSystem("loader")

  val nb_clients = args(0).toInt
  args(1) match {
    case "couch" => new CouchLoader(nb_clients).run
    case "mongo" => new MongoLoader(nb_clients).run
  }
}

trait Loader {
  def nb_clients: Int
  def clients: List[ActorRef]
  def init: Future[_]
  def load(docs: Seq[String]): Unit

  import scala.concurrent.ExecutionContext.Implicits.global
  def run = init map { _ => 
    io.Source
      // sample file available from https://github.com/zemirco/sf-city-lots-json/blob/master/citylots.json
      .fromFile("/tmp/sf-city-lots-json/citylots.json") 
      .getLines//.toStream
      .drop(3) //drop the wrapper json
      .filter(line => !(line.size < 2)) // and intermediate commas.. 
      .grouped(nb_clients) // mk groups of size nb_clients
      .foreach(load(_))
  }
}


import spray.http.{ HttpRequest, HttpResponse }
import spray.client.pipelining._ //{ Get, Put, Post, sendReceive, addHeader }
import spray.httpx.SprayJsonSupport
import spray.json.AdditionalFormats

import akka.util.Timeout
import scala.concurrent.duration._

class CouchLoader(val nb_clients:Int)(implicit akka: ActorSystem) extends Loader {
  import CouchClient._
  import akka.dispatcher // execution context for futures

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  val clients = List.fill(nb_clients)(akka.actorOf(Props[CouchClient]))

  val db = "load-test"
  
  def init = pipeline(Put(s"$couch/$db"))


  def load(docs: Seq[String]) = (docs zip clients) map { //round robin dispatch to each client
    case (entry, client) => client ! Doc(db, entry)
  }
}

object CouchClient {
  val couch = "http://localhost:49153"
  
  implicit val timeout: Timeout = 10 seconds

  case class Doc(db: String, doc: String)//, uuid: String)
}

class CouchClient extends Actor {
  import CouchClient._
  import context.dispatcher // execution context for futures

  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  def receive = {
    case Doc(db, doc) => pipeline(Post(s"$couch/$db/", doc.asJson.asObject))
  }
}


import com.mongodb._
import com.mongodb.util.JSON

class MongoLoader(val nb_clients:Int)(implicit akka: ActorSystem) extends Loader {
  import MongoDBClient._

  val clients = List.fill(nb_clients)(akka.actorOf(Props[MongoDBClient]))

  import scala.concurrent.ExecutionContext.Implicits.global
  def init = Future{}

  def load(docs: Seq[String]) = (docs zip clients) map {
    case (doc, client) => client ! Doc(doc)
  }
}

object MongoDBClient {
  // singleton pool of connections to mongo
  val mongo = new MongoClient("localhost", 27017)
  val db = "load-test"
  case class Doc(doc:String)
}
class MongoDBClient extends Actor {
  import MongoDBClient._

  def receive = {
    case Doc(doc) => 
      mongo.getDB(db).getCollection(db)
        .insert(JSON.parse(doc).asInstanceOf[DBObject], WriteConcern.ACKNOWLEDGED)
  }
}
