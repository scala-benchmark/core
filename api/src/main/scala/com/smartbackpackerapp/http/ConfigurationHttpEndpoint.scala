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
import org.http4s._
import org.http4s.dsl.Http4sDsl

import scala.util.matching.Regex
import scala.xml.XML
import javax.xml.parsers.SAXParserFactory

class ConfigurationHttpEndpoint[F[_] : Sync : Monad] extends Http4sDsl[F] {

  object PatternQueryParam extends QueryParamDecoderMatcher[String]("pattern")
  object DataQueryParam extends QueryParamDecoderMatcher[String]("data")
  object XmlContentQueryParam extends QueryParamDecoderMatcher[String]("xmlContent")

  private def validatePatternSyntax(pattern: String): String = {
    if (pattern.isEmpty) {
      println(s"Warning: Pattern is empty")
    }
    pattern
  }

  private def validatePatternLength(pattern: String): String = {
    if (pattern.length > 1000) {
      println(s"Warning: Pattern exceeds recommended length: ${pattern.length}")
    }
    pattern
  }

  private def validateXmlStructure(xml: String): String = {
    if (!xml.trim.startsWith("<")) {
      println(s"Warning: XML content does not appear to be valid XML")
    }
    xml
  }

  private def validateXmlEncoding(xml: String): String = {
    if (!xml.contains("encoding")) {
      println(s"Warning: XML does not specify encoding")
    }
    xml
  }

  val service: AuthedService[String, F] = AuthedService {
    //SOURCE
    case GET -> Root / ApiVersion / "search" / "pattern" :? PatternQueryParam(pattern) +& DataQueryParam(data) as _ =>
      val validatedPattern = validatePatternLength(validatePatternSyntax(pattern))
      
      for {
        result <- Sync[F].delay {
          val regex = new Regex(validatedPattern)
          //CWE 1333
          //SINK
          regex.findFirstIn(data) match {
            case Some(matched) => s"Found: $matched"
            case None => "No match found"
          }
        }
        response <- Ok(buildSearchResultHtml(result, pattern, data)).map(_.withContentType(headers.`Content-Type`(MediaType.`text/html`)))
      } yield response

    //SOURCE
    case req @ POST -> Root / ApiVersion / "config" / "import" as _ =>
      for {
        body <- req.req.as[String]
        validatedXml = validateXmlEncoding(validateXmlStructure(body))
        result <- Sync[F].delay {
          val factory = SAXParserFactory.newInstance()
          factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
          factory.setFeature("http://xml.org/sax/features/external-general-entities", true)
          factory.setFeature("http://xml.org/sax/features/external-parameter-entities", true)
          val saxParser = factory.newSAXParser()
          
          //CWE 611
          //SINK
          val xmlDoc = XML.withSAXParser(saxParser).loadString(validatedXml)
          
          val configName = (xmlDoc \ "name").text
          val configValue = (xmlDoc \ "value").text
          
          System.setProperty("IMPORTED_CONFIG_NAME", configName)
          System.setProperty("IMPORTED_CONFIG_VALUE", configValue)
          
          s"Configuration imported: $configName"
        }
        response <- Ok(s"Success: $result")
      } yield response
  }

  private def buildSearchResultHtml(result: String, pattern: String, data: String): String = {
    s"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Pattern Search Results</title>
  <style>
    :root {
      --bg-primary: #0a0e17;
      --bg-secondary: #111827;
      --accent: #06b6d4;
      --accent-hover: #22d3ee;
      --text-primary: #f0f4f8;
      --text-muted: #94a3b8;
      --border: rgba(6, 182, 212, 0.2);
      --success: #10b981;
      --warning: #f59e0b;
    }
    
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }
    
    body {
      font-family: 'JetBrains Mono', 'Fira Code', monospace;
      background: linear-gradient(135deg, var(--bg-primary) 0%, #0f172a 50%, var(--bg-secondary) 100%);
      min-height: 100vh;
      color: var(--text-primary);
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 2rem;
    }
    
    .container {
      background: rgba(17, 24, 39, 0.8);
      backdrop-filter: blur(10px);
      border: 1px solid var(--border);
      border-radius: 16px;
      padding: 2.5rem;
      max-width: 700px;
      width: 100%;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5),
                  0 0 0 1px rgba(6, 182, 212, 0.1);
    }
    
    .header {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-bottom: 2rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--border);
    }
    
    .icon {
      width: 48px;
      height: 48px;
      background: linear-gradient(135deg, var(--accent), #0891b2);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
    }
    
    h1 {
      font-size: 1.5rem;
      font-weight: 600;
      background: linear-gradient(90deg, var(--accent), var(--accent-hover));
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    
    .result-box {
      background: rgba(6, 182, 212, 0.1);
      border: 1px solid var(--accent);
      border-radius: 12px;
      padding: 1.5rem;
      margin-bottom: 1.5rem;
    }
    
    .result-label {
      font-size: 0.75rem;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--accent);
      margin-bottom: 0.5rem;
    }
    
    .result-value {
      font-size: 1.1rem;
      color: var(--success);
      word-break: break-all;
    }
    
    .details {
      display: grid;
      gap: 1rem;
    }
    
    .detail-item {
      background: rgba(255, 255, 255, 0.02);
      border-radius: 8px;
      padding: 1rem;
    }
    
    .detail-label {
      font-size: 0.7rem;
      text-transform: uppercase;
      letter-spacing: 0.1em;
      color: var(--text-muted);
      margin-bottom: 0.25rem;
    }
    
    .detail-value {
      font-size: 0.9rem;
      color: var(--text-primary);
      word-break: break-all;
    }
    
    .footer {
      margin-top: 2rem;
      padding-top: 1.5rem;
      border-top: 1px solid var(--border);
      text-align: center;
      color: var(--text-muted);
      font-size: 0.75rem;
    }
  </style>
</head>
<body>
  <div class="container">
    <div class="header">
      <div class="icon">🔍</div>
      <h1>Pattern Search Results</h1>
    </div>
    
    <div class="result-box">
      <div class="result-label">Match Result</div>
      <div class="result-value">$result</div>
    </div>
    
    <div class="details">
      <div class="detail-item">
        <div class="detail-label">Pattern Used</div>
        <div class="detail-value">$pattern</div>
      </div>
      <div class="detail-item">
        <div class="detail-label">Input Data</div>
        <div class="detail-value">$data</div>
      </div>
    </div>
    
    <div class="footer">
      Smart Backpacker API • Pattern Matching Engine
    </div>
  </div>
</body>
</html>"""
  }

}
