package ap.test.nosql

import akka.actor._

object Main extends App {

  val akka = ActorSystem("loader")

  val nb_clients = args(0).toInt
  args(1) match {
    case "couch" => new CouchLoader(akka, nb_clients).run
    case "mongo" => new MongoLoader(akka, nb_clients).run
  }
}

trait Loader {
  def nb_clients: Int
  def clients: List[ActorRef]
  def init: Unit
  def load(docs: Seq[String]): Unit

  def run = {
    init
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


import com.mashape.unirest.http.Unirest

class CouchLoader(akka: ActorSystem, val nb_clients:Int) extends Loader {
  import CouchClient._

  val clients = List.fill(nb_clients)(akka.actorOf(Props[CouchClient]))

  val db = "load-test"
  
  def init: Unit = println(Unirest.put(s"$couch/$db").asJson.getBody)//clients(0) ! DB(db)


  def load(docs: Seq[String]) = 
  {
    // get enough uids in one request for this batch
    //val uids = Unirest.get(s"$couch/_uuids?count=$nb_clients").asJson.getBody.getObject.getJSONArray("uuids")
    (docs zip clients).zipWithIndex map { //round robin dispatch to each client
      case ((entry, client), index) => client ! Doc(db, entry, "")//uids.get(index).toString)
    }
  }
}

object CouchClient {
  val couch = "http://localhost:49153"
  case object Server
  case object DBs
  case class DB(db:String)
  case class Doc(db: String, doc: String, uuid: String)
}

class CouchClient extends Actor {
  import CouchClient._

  def receive = {
    case Server => println(Unirest.get(couch).asJson.getBody.getObject.toString(2))
    case DBs    => println(Unirest.get(s"$couch/_all_dbs").asJson.getBody)
    case DB(db) => println(Unirest.put(s"$couch/$db").asJson.getBody)
    case Doc(db, doc, uuid) => 
      //println(Unirest.post(s"$couch/$db/").body(doc).toString)
      Unirest.post(s"$couch/$db/")
        .header("Content-Type", "application/json")
        .body(doc).asJson.getBody//println()
      //Unirest.put(s"$couch/$db/$uuid").body(doc).asJson.getBody//println()
  }
}


import com.mongodb._
import com.mongodb.util.JSON

class MongoLoader(akka: ActorSystem, val nb_clients:Int) extends Loader {
  import MongoDBClient._

  val clients = List.fill(nb_clients)(akka.actorOf(Props[MongoDBClient]))

  def init: Unit = {}

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
