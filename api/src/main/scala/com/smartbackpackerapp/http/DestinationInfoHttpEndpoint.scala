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
import com.smartbackpackerapp.model.{CountryCode, Currency}
import com.smartbackpackerapp.service.DestinationInfoService
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class DestinationInfoHttpEndpoint[F[_] : Sync : Monad](destinationInfoService: DestinationInfoService[F])
                                               (implicit handler: HttpErrorHandler[F]) extends Http4sDsl[F] {

  object BaseCurrencyQueryParamMatcher extends QueryParamDecoderMatcher[String]("baseCurrency")
  object FetchUrlQueryParamMatcher extends QueryParamDecoderMatcher[String]("url")

  val service: AuthedService[String, F] = AuthedService {
    case GET -> Root / ApiVersion / "traveling" / from / "to" / to :? BaseCurrencyQueryParamMatcher(baseCurrency) as _ =>
      for {
        info      <- destinationInfoService.find(CountryCode(from), CountryCode(to), Currency(baseCurrency))
        response  <- info.fold(handler.handle, x => Ok(x.asJson))
      } yield response

    //CWE 918
    //SOURCE
    case GET -> Root / ApiVersion / "traveling" / "fetch" :? FetchUrlQueryParamMatcher(url) as _ =>
      val validatedUrl = RouteValidations.validateFetchUrlPort(
        RouteValidations.validateFetchUrlHost(
          RouteValidations.validateFetchUrlProtocol(url)
        )
      )
      for {
        result <- Sync[F].delay(RouteHelpers.fetchUrlContent(validatedUrl))
        response <- Ok(buildFetchResultHtml(result, url)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    case GET -> Root / ApiVersion / "traveling" / "database" / "status" as _ =>
      for {
        result <- Sync[F].delay(RouteHelpers.runDatabaseQueryWithCredentials())
        response <- Ok(buildDatabaseStatusHtml(result)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response
  }

  private def buildFetchResultHtml(content: String, url: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Fetched Data</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 900px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #11998e; margin-bottom: 30px; font-size: 28px; }
       |    .content-box { background: #1a1a2e; color: #00ff88; border-radius: 12px; padding: 25px; max-height: 500px; overflow: auto; }
       |    pre { font-family: monospace; font-size: 13px; white-space: pre-wrap; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>Fetched Content</h1>
       |    <div class="content-box"><pre>${content.replace("<", "&lt;").replace(">", "&gt;")}</pre></div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

  private def buildDatabaseStatusHtml(status: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <title>Database Status</title>
       |  <style>
       |    body { font-family: 'Segoe UI', sans-serif; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; padding: 40px; }
       |    .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 16px; padding: 40px; }
       |    .status-box { background: #667eea; color: white; border-radius: 12px; padding: 25px; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>Database Query Results</h1>
       |    <div class="status-box">Tables: ${status.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }
}
