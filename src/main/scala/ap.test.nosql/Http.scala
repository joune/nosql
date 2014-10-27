package ap.test.nosql

import java.net.{HttpURLConnection, URL, URLEncoder, URLDecoder}
import java.io.{DataOutputStream, InputStream, BufferedReader, InputStreamReader, ByteArrayInputStream, ByteArrayOutputStream}


case class HttpException(val code: Int, val message: String, val body: String, cause: Throwable) extends 
  RuntimeException(code + ": " + message, cause)

object Http {
  def apply(url: String):Request = get(url)
  
  type HttpExec = (Request, HttpURLConnection) => Unit
  type HttpUrl = Request => URL
  
  case class Request(method: String, url: HttpUrl, exec: HttpExec, params: List[(String,String)] = Nil, 
      headers: List[(String,String)] = Nil, charset: String = Http.utf8) {

    def headers(h: (String,String)*):Request = headers(h.toList)
    def headers(h: Map[String, String]):Request = headers(h.toList)
    def headers(h: List[(String,String)]):Request = copy(headers = headers ++ h)

    def header(key: String, value: String):Request = headers(key -> value)

    def apply[T](parser: InputStream => T): T = process((conn:HttpURLConnection) => tryParse(conn.getInputStream(), parser))
    
    def process[T](processor: HttpURLConnection => T): T = {

      def getErrorBody(errorStream: InputStream): String = {
        if (errorStream != null) {
          tryParse(errorStream, readString(_, charset))  
        } else ""
      }

      url(this).openConnection match {
        case conn: HttpURLConnection =>
          conn.setInstanceFollowRedirects(true)
          headers.reverse.foreach{case (name, value) => 
            conn.setRequestProperty(name, value)
          }

          exec(this, conn)
          try {
            processor(conn)
          } catch {
            case e: java.io.IOException =>
              throw new HttpException(conn.getResponseCode, conn.getResponseMessage, 
                getErrorBody(conn.getErrorStream), e)
          }
      }
    }
    
    def asString: String = apply(readString(_, charset))

    def responseCode: Int = process{(conn:HttpURLConnection) =>
      closeStreams(conn)
      conn.getResponseCode
    }

    private def closeStreams(conn:HttpURLConnection) {
      try { conn.getInputStream().close } catch {
        case e: Exception => //ignore
      }
      try { conn.getErrorStream().close } catch {
        case e: Exception => //ignore
      }
    }

  }

  def tryParse[E](is: InputStream, parser: InputStream => E): E = try {
    parser(is)
  } finally {
    is.close
  }

  def readString(is: InputStream): String = readString(is, utf8)
  /**
   * [lifted from lift]
   */
  def readString(is: InputStream, charset: String): String = {
    val in = new InputStreamReader(is, charset)
    val bos = new StringBuilder
    val ba = new Array[Char](4096)

    def readOnce {
      val len = in.read(ba)
      if (len > 0) bos.appendAll(ba, 0, len)
      if (len >= 0) readOnce
    }

    readOnce
    bos.toString
  }

  def noopHttpUrl(url :String): HttpUrl = r => new URL(url)
  
  def get(url: String) = noopRequest("GET", url)
  def put(url: String) = noopRequest("PUT", url)
  def noopRequest(method: String, url: String): Request = {
    val getFunc: HttpExec = (req,conn) => {
      conn.setRequestMethod(method)
      conn.connect
    }
    
    Request(method, noopHttpUrl(url), getFunc)
  }
  
  def postData(url: String, data: String): Request = postData(url, data.getBytes(utf8))
  def postData(url: String, data: Array[Byte]): Request = {
    val postFunc: HttpExec = (req, conn) => {
      conn.setRequestMethod(req.method)
      conn.setConnectTimeout(100)
      conn.setReadTimeout(500)
      conn.setDoOutput(true)
      conn.connect
      conn.getOutputStream.write(data)
    }
    Request("POST", noopHttpUrl(url), postFunc)
  }

  val utf8 = "UTF-8"
}


