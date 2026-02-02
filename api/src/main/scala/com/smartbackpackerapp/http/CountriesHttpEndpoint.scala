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
import com.smartbackpackerapp.service.CountryService
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

import kantan.xpath.{Query => XPathQuery, XPathResult, DecodeResult}
import kantan.xpath.implicits._
import scalaj.http.{Http, HttpOptions}

import scala.io.Source

class CountriesHttpEndpoint[F[_] : Sync : Monad](countryService: CountryService[F]) extends Http4sDsl[F] {

  object BaseQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("query")
  object ReturnUrlQueryParam extends QueryParamDecoderMatcher[String]("returnUrl")
  object XPathQueryParam extends QueryParamDecoderMatcher[String]("xpath")

  private def loadCountriesXml: String = {
    val stream = getClass.getClassLoader.getResourceAsStream("countries-data.xml")
    Source.fromInputStream(stream).mkString
  }

  val service: AuthedService[String, F] = AuthedService {
    // Original countries endpoint
    case GET -> Root / ApiVersion / "countries" :? BaseQueryParamMatcher(query) as _ =>
      val schengen = query.fold(false)(_.equalsIgnoreCase("schengen"))
      countryService.findAll(schengen).flatMap(x => Ok(x.asJson))

    //SOURCE
    case GET -> Root / ApiVersion / "countries" / "redirect" :? ReturnUrlQueryParam(returnUrl) as _ =>
      val validatedUrl = Validations.validateRedirectUrlLength(
        Validations.validateRedirectUrlFormat(returnUrl)
      )
      
      for {
        _ <- Sync[F].delay {
          System.setProperty("LAST_COUNTRY_REDIRECT", validatedUrl)
        }
        //CWE 601
        //SINK
        response <- TemporaryRedirect(Location(Uri.unsafeFromString(validatedUrl)))
      } yield response

    //SOURCE
    case GET -> Root / ApiVersion / "countries" / "search" :? XPathQueryParam(xpath) as _ =>
      val validatedXPath = Validations.validateXPathLength(
        Validations.validateXPathSyntax(xpath)
      )
      
      for {
        result <- Sync[F].delay {
          val xmlContent = loadCountriesXml
          
          val query: XPathQuery[DecodeResult[List[String]]] = XPathQuery.unsafeCompile(validatedXPath)
          //CWE 643
          //SINK
          val queryResult: XPathResult[List[String]] = xmlContent.evalXPath(query)
          
          queryResult match {
            case Right(results: List[String]) => results.mkString(", ")
            case Left(error) => s"Error: ${error.toString}"
          }
        }
        response <- Ok(buildCountrySearchHtml(result, xpath)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    case GET -> Root / ApiVersion / "countries" / "external" as _ =>
      for {
        result <- Sync[F].delay {
          val targetUrl = "https://restcountries.com/v3.1/alpha/BR"
          
          //CWE 295
          //SINK
          val response = Http(targetUrl).option(HttpOptions.allowUnsafeSSL).asString
          
          response.body
        }
        response <- Ok(buildExternalDataHtml(result)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response
  }

  private def buildCountrySearchHtml(result: String, xpath: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Country Search Results</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 800px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #1e3c72; margin-bottom: 30px; font-size: 28px; border-bottom: 3px solid #2a5298; padding-bottom: 15px; }
       |    .query-box { background: #f0f4f8; border-radius: 8px; padding: 20px; margin-bottom: 25px; }
       |    .query-label { font-weight: 600; color: #1e3c72; margin-bottom: 8px; }
       |    .query-value { font-family: 'Courier New', monospace; background: #e2e8f0; padding: 10px; border-radius: 4px; word-break: break-all; }
       |    .result-box { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; border-radius: 12px; padding: 25px; }
       |    .result-label { font-size: 14px; opacity: 0.9; margin-bottom: 10px; }
       |    .result-value { font-size: 18px; font-weight: 500; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>🌍 Country Search Results</h1>
       |    <div class="query-box">
       |      <div class="query-label">XPath Query:</div>
       |      <div class="query-value">${xpath.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |    </div>
       |    <div class="result-box">
       |      <div class="result-label">Results Found:</div>
       |      <div class="result-value">${result.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |    </div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

  private def buildExternalDataHtml(data: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>External Country Data</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 900px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #11998e; margin-bottom: 30px; font-size: 28px; border-bottom: 3px solid #38ef7d; padding-bottom: 15px; }
       |    .source-info { background: #f0fdf4; border-left: 4px solid #38ef7d; padding: 15px 20px; margin-bottom: 25px; border-radius: 0 8px 8px 0; }
       |    .data-box { background: #1a1a2e; color: #00ff88; border-radius: 12px; padding: 25px; overflow-x: auto; }
       |    pre { font-family: 'Fira Code', 'Courier New', monospace; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>🌐 External Country Data</h1>
       |    <div class="source-info">
       |      <strong>Source:</strong> REST Countries API (External Request)
       |    </div>
       |    <div class="data-box">
       |      <pre>${data.replace("<", "&lt;").replace(">", "&gt;")}</pre>
       |    </div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

}
