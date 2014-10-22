package ap.test.nosql

import java.net._
import java.io._
import scala.util.Try

trait HttpClient {
  // mutable buffer is used sequentially with each actor
  private val buf = new Array[Byte](4096)

  def get(uri: String) = runHttp(openConnection(uri))

  def put(uri: String) = {
    val cnx = openConnection(uri)
    cnx.setRequestMethod("PUT")
    runHttp(cnx)
  }

  def post(uri: String, json: String) = {
    val cnx = openConnection(uri)
    cnx.setRequestMethod("POST")
    cnx.setDoOutput(true)
    cnx.setRequestProperty("Content-Type", "application/json")
    val out = new OutputStreamWriter(cnx.getOutputStream)
    out.write(json, 0, json.length)
    out.close
    runHttp(cnx)
  }

  private def openConnection(uri: String) = new URL(uri).openConnection.asInstanceOf[HttpURLConnection]

  private def runHttp(cnx: HttpURLConnection) = Try {
    val in = cnx.getInputStream
    while (in.read(buf) > 0) {
      // drop!
    }
    in.close
  } recover {
    case x: IOException => {
      println(cnx.getResponseCode)
      val es = cnx.getErrorStream
      while (es.read(buf) > 0) {
        // drop!
      }
      es.close
    }
  } recover {
    case e => new Exception(cnx.getURL.toString, e).printStackTrace
  }
}


