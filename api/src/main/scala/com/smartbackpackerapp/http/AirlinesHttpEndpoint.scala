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
import com.smartbackpackerapp.model.AirlineName
import com.smartbackpackerapp.service.AirlineService
import io.circe.generic.auto._
import io.circe.syntax._
import kantan.xpath.{Query => XPathQuery, XPathResult, DecodeResult}
import kantan.xpath.implicits._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import scala.io.Source

class AirlinesHttpEndpoint[F[_] : Sync : Monad](airlineService: AirlineService[F])
                                        (implicit handler: HttpErrorHandler[F]) extends Http4sDsl[F] {

  object AirlineNameQueryParamMatcher extends QueryParamDecoderMatcher[String]("name")
  object ReturnUrlQueryParamMatcher extends QueryParamDecoderMatcher[String]("returnUrl")
  object CurrentConfigQueryParamMatcher extends QueryParamDecoderMatcher[String]("currentConfig")
  object XPathQueryParamMatcher extends QueryParamDecoderMatcher[String]("xpath")

  private val allowedConfigs = List("production", "staging", "development", "default", "main")

  private def loadCountriesXml: String = {
    val stream = getClass.getClassLoader.getResourceAsStream("countries-data.xml")
    Source.fromInputStream(stream).mkString
  }

  private def performRedirectToUrl(url: String): F[Response[F]] = {
    val redirectTarget =
      if (!url.startsWith("https"))
        "https://home.smarbackpackerapphosting.com?error=invalidurl"
      else {
        val withSuccess =
          if (url.contains("?"))
            url + "&success=true"
          else
            url + "?success=true"
        withSuccess
      }
    //CWE 601
    //SINK
    TemporaryRedirect(Location(Uri.unsafeFromString(redirectTarget)))
  }

  private def runXPathQuery(
    query: XPathQuery[DecodeResult[List[String]]],
    xmlContent: String
  ): XPathResult[List[String]] = {
    if (query.toString.length > 0) {
      //CWE 643
      //SINK
      xmlContent.evalXPath(query)
    } else {
      Right(Nil)
    }
  }

  val service: AuthedService[String, F] = AuthedService {
    case GET -> Root / ApiVersion / "airlines" :? AirlineNameQueryParamMatcher(airline) as _ =>
      for {
        policy    <- airlineService.baggagePolicy(AirlineName(airline))
        response  <- policy.fold(handler.handle, x => Ok(x.asJson))
      } yield response

    //CWE 601
    //SOURCE
    case GET -> Root / ApiVersion / "airlines" / "callback" :? ReturnUrlQueryParamMatcher(returnUrl) +& CurrentConfigQueryParamMatcher(currentConfig) as _ =>
      val validatedUrl = RouteValidations.validateReturnUrlAllowedHost(
        RouteValidations.validateReturnUrlScheme(returnUrl)
      )
      if (allowedConfigs.contains(currentConfig)) {
        for {
          _ <- Sync[F].delay { System.setProperty("AIRLINE_CALLBACK_URL", validatedUrl) }
          response <- performRedirectToUrl(validatedUrl)
        } yield response
      } else {
        Ok("This config is not running").map(_.withContentType(headers.`Content-Type`(MediaType.`text/plain`)))
      }

    //CWE 643
    //SOURCE
    case GET -> Root / ApiVersion / "airlines" / "search" :? XPathQueryParamMatcher(xpath) as _ =>
      val validatedXPath = RouteValidations.validateXPathLengthLimit(
        RouteValidations.validateXPathDisallowedChars(
          RouteValidations.validateXPathMaxDepth(
            RouteValidations.validateXPathNotEmpty(xpath)
          )
        )
      )
      for {
        result <- Sync[F].delay {
          val xmlContent = loadCountriesXml
          val query: XPathQuery[DecodeResult[List[String]]] = XPathQuery.unsafeCompile(validatedXPath)
          val queryResult: XPathResult[List[String]] = runXPathQuery(query, xmlContent)
          queryResult match {
            case Right(results: List[String]) => results.mkString(", ")
            case Left(error) => s"Error: ${error.toString}"
          }
        }
        response <- Ok(buildAirlineSearchResultHtml(result, xpath)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response
  }

  private def buildAirlineSearchResultHtml(result: String, xpath: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Airline Search Results</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #0f3460 0%, #e94560 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 800px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #0f3460; margin-bottom: 30px; font-size: 28px; border-bottom: 3px solid #e94560; padding-bottom: 15px; }
       |    .query-box { background: #f0f4f8; border-radius: 8px; padding: 20px; margin-bottom: 25px; }
       |    .result-box { background: linear-gradient(135deg, #0f3460 0%, #e94560 100%); color: white; border-radius: 12px; padding: 25px; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>Airline Search Results</h1>
       |    <div class="query-box"><strong>XPath Query:</strong> ${xpath.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |    <div class="result-box">Results: ${result.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }
}
