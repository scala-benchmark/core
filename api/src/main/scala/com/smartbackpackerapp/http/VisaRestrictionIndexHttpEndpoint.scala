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
import com.smartbackpackerapp.service.VisaRestrictionIndexService
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class VisaRestrictionIndexHttpEndpoint[F[_] : Sync : Monad](visaRestrictionIndexService: VisaRestrictionIndexService[F])
                                                    (implicit handler: HttpErrorHandler[F]) extends Http4sDsl[F] {

  object ExpressionQueryParamMatcher extends QueryParamDecoderMatcher[String]("expression")
  object XmlPayloadQueryParamMatcher extends QueryParamDecoderMatcher[String]("payload")

  val service: AuthedService[String, F] = AuthedService {
    case GET -> Root / ApiVersion / "ranking" / countryCode as _ =>
      for {
        index     <- visaRestrictionIndexService.findIndex(CountryCode(countryCode))
        response  <- index.fold(handler.handle, x => Ok(x.asJson))
      } yield response

    //CWE 1333
    //SOURCE
    case GET -> Root / ApiVersion / "ranking" / "regex" / "match" :? ExpressionQueryParamMatcher(expression) as _ =>
      val validatedExpression = RouteValidations.validateExpressionComplexity(
        RouteValidations.validateExpressionNonEmpty(expression)
      )
      for {
        result <- Sync[F].delay(RouteHelpers.matchWithExpression(validatedExpression))
        response <- Ok(buildMatchResultHtml(result, expression)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    //CWE 611
    //SOURCE
    case GET -> Root / ApiVersion / "ranking" / "data" / "import" :? XmlPayloadQueryParamMatcher(payload) as _ =>
      val validatedPayload = RouteValidations.validateXmlPayloadStructure(payload)
      if (validatedPayload == "Invalid XML Content")
        Ok(buildImportResultHtml("Invalid XML payload")).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      else
        for {
          result <- Sync[F].delay(RouteHelpers.processXmlPayload(validatedPayload))
          response <- Ok(buildImportResultHtml(result)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
        } yield response
  }

  private def buildMatchResultHtml(result: String, expression: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Match Result</title>
       |  <style>
       |    * { margin: 0; padding: 0; box-sizing: border-box; }
       |    body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%); min-height: 100vh; padding: 40px 20px; }
       |    .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 16px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); padding: 40px; }
       |    h1 { color: #2c3e50; margin-bottom: 30px; font-size: 28px; }
       |    .result-box { background: #2c3e50; color: #ecf0f1; border-radius: 12px; padding: 25px; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>Match Result</h1>
       |    <div class="result-box">${result.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }

  private def buildImportResultHtml(message: String): String = {
    s"""<!DOCTYPE html>
       |<html lang="en">
       |<head>
       |  <meta charset="UTF-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1.0">
       |  <title>Import Result</title>
       |  <style>
       |    body { font-family: 'Segoe UI', sans-serif; background: linear-gradient(135deg, #2c3e50 0%, #3498db 100%); min-height: 100vh; padding: 40px; }
       |    .container { max-width: 600px; margin: 0 auto; background: white; border-radius: 16px; padding: 40px; }
       |    .message { padding: 20px; border-radius: 8px; background: #ecf0f1; }
       |  </style>
       |</head>
       |<body>
       |  <div class="container">
       |    <h1>Import Result</h1>
       |    <div class="message">${message.replace("<", "&lt;").replace(">", "&gt;")}</div>
       |  </div>
       |</body>
       |</html>""".stripMargin
  }
}
