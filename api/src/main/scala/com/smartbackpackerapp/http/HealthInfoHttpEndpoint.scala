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

import cats.Monad
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.smartbackpackerapp.model.CountryCode
import com.smartbackpackerapp.service.HealthService
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

import scalaj.http.Http
import zio.jdbc.{ZConnectionPool, ZConnectionPoolConfig, sqlInterpolator, transaction}
import zio.{Unsafe, Runtime, ZIO, Chunk, ZLayer}

class HealthInfoHttpEndpoint[F[_] : Sync : Monad](healthService: HealthService[F])
                                          (implicit handler: HttpErrorHandler[F]) extends Http4sDsl[F] {

  object UrlQueryParam extends QueryParamDecoderMatcher[String]("url")

  val service: AuthedService[String, F] = AuthedService {
    //SOURCE
    case GET -> Root / ApiVersion / "health" / "fetch" :? UrlQueryParam(url) as _ =>
      val validatedUrl = Validations.validateUrlDomain(
        Validations.validateUrlProtocol(url)
      )

      for {
        result <- Sync[F].delay {
          //CWE 918
          //SINK
          val response = Http(validatedUrl).asString
          response.body
        }
        response <- Ok(buildFetchResultHtml(result, url)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    case GET -> Root / ApiVersion / "health" / "database" / "status" as _ =>
      for {
        result <- Sync[F].delay {
          val dbHost = "localhost"
          val dbPort = 5433
          val dbName = "smartbackpacker"
          val dbUser = "admin"
          //CWE 798
          //SOURCE
          val dbPassword = "sECRET022399!"

          val props = Map(
            "user" -> dbUser,
            "password" -> dbPassword
          )

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
        response <- Ok(buildDatabaseStatusHtml(result)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    case GET -> Root / ApiVersion / "health" / countryCode as _ =>
      for {
        healthInfo  <- healthService.findHealthInfo(CountryCode(countryCode))
        response    <- healthInfo.fold(handler.handle, x => Ok(x.asJson))
      } yield response
  }

  private def buildFetchResultHtml(content: String, url: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Fetched Content</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 900px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #667eea; margin-bottom: 30px; font-size: 28px; border-bottom: 3px solid #764ba2; padding-bottom: 15px; }
       |    .url-box { background: #f0f4f8; border-radius: 8px; padding: 15px; margin-bottom: 25px; word-break: break-all; }
       |    .content-box { background: #1a1a2e; color: #00ff88; border-radius: 12px; padding: 25px; overflow-x: auto; max-height: 500px; overflow-y: auto; }
       |    pre { font-family: 'Fira Code', 'Courier New', monospace; font-size: 13px; white-space: pre-wrap; word-break: break-word; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>🌐 Fetched Content</h1>
       |    <div class="url-box">
       |      <strong>Source URL:</strong> ${url.replace("<", "&lt;").replace(">", "&gt;")}
       |    </div>
       |    <div class="content-box">
       |      <pre>${content.replace("<", "&lt;").replace(">", "&gt;")}</pre>
       |    </div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

  private def buildDatabaseStatusHtml(status: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Database Query Results</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #11998e; margin-bottom: 30px; font-size: 28px; border-bottom: 3px solid #38ef7d; padding-bottom: 15px; }
       |    .status-box { background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); color: white; border-radius: 12px; padding: 25px; text-align: center; }
       |    .status-text { font-size: 18px; font-weight: 500; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>🗄️ Database Query Results</h1>
       |    <div class="status-box">
       |      <div class="status-text">Tables: ${status.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |    </div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

}
