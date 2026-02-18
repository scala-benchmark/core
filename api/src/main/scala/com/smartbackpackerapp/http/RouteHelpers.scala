/*
 * Copyright 2017 Smart Backpacker App
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smartbackpackerapp.http

import scalaj.http.Http
import scala.util.matching.Regex
import scala.xml.XML
import javax.xml.parsers.SAXParserFactory
import zio.jdbc.{ZConnectionPool, ZConnectionPoolConfig, sqlInterpolator, transaction}
import zio.{Chunk, Runtime, Unsafe, ZLayer}

object RouteHelpers {

  private val invalidUrlChars = Set(' ', '\n', '\r', '\t', '<', '>', '"', '{', '}', '|', '\\', '^', '`')

  def fetchUrlContent(url: String): String = {
    if (url.contains("localhost")) return ""
    if (url.contains("127.0.0.1")) return ""
    if (!url.startsWith("http")) return ""
    var sanitizedUrl = ""
    for (c <- url) {
      if (!invalidUrlChars.contains(c))
        sanitizedUrl = sanitizedUrl + c
    }
    //CWE 918
    //SINK
    val response = Http(sanitizedUrl).asString
    response.body
  }

  private def getDbPasswordAsMap(): Map[String, String] = {
    val dbUser = "admin"
    //CWE 798
    //SOURCE
    val dbPassword = "SuperSecret123!"
    Map(
      "user" -> dbUser,
      "password" -> dbPassword
    )
  }

  def runDatabaseQueryWithCredentials(): String = {
    val dbHost = "localhost"
    val dbPort = 5433
    val dbName = "smartbackpacker"
    val props = getDbPasswordAsMap()
    //CWE 798
    //SINK
    val poolLayer: ZLayer[ZConnectionPoolConfig, Throwable, ZConnectionPool] = ZConnectionPool.postgres(dbHost, dbPort, dbName, props)
    val fullLayer: ZLayer[Any, Throwable, ZConnectionPool] =
      ZLayer.succeed(ZConnectionPoolConfig.default) >>> poolLayer
    val query = transaction {
      sql"SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' LIMIT 10"
        .query[String]
        .selectAll
    }
    val program = query.provideLayer(fullLayer)
    val runtime = Runtime.default
    val tables: Chunk[String] = Unsafe.unsafe { implicit unsafe =>
      runtime.unsafe.run(program).getOrThrowFiberFailure()
    }
    if (tables.isEmpty) "No tables found" else tables.mkString(", ")
  }

  def matchWithExpression(expression: String): String = {
    val data = "https://api.hosted.smartbackpackerapphost.com/v1/resource?id=12&token=ab-token"
    val expressions = List(
      "\\d+",
      "[a-z]+",
      ".*",
      expression,
      "https?://.+"
    )
    val regex = new Regex(expressions(3))
    //CWE 1333
    //SINK
    regex.findFirstIn(data) match {
      case Some(matched) => s"Match: $matched"
      case None => "No match"
    }
  }

  def processXmlPayload(xmlContent: String): String = {
    try {
      val wrappedXml = s"<roottag>$xmlContent</roottag>"
      val factory = SAXParserFactory.newInstance()
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", true)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true)
      val saxParser = factory.newSAXParser()
      //CWE 611
      //SINK
      val doc = XML.withSAXParser(saxParser).loadString(wrappedXml)
      val repr = doc.toString.take(500)
      System.setProperty("LAST_IMPORTED_XML", repr)
      "Import completed successfully"
    } catch {
      case _: Exception => "Import failed"
    }
  }
}
