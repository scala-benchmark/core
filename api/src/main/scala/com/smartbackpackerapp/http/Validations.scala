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

object Validations {

  def validateRedirectUrlFormat(url: String): String = {
    if (!url.startsWith("http")) {
      println(s"Warning: Redirect URL does not start with http: $url")
    }
    url
  }

  def validateRedirectUrlLength(url: String): String = {
    if (url.length > 2048) {
      println(s"Warning: Redirect URL exceeds maximum length: ${url.length}")
    }
    url
  }

  def validateXPathSyntax(xpath: String): String = {
    if (xpath.isEmpty) {
      println(s"Warning: XPath expression is empty")
    }
    xpath
  }

  def validateXPathLength(xpath: String): String = {
    if (xpath.length > 500) {
      println(s"Warning: XPath expression exceeds recommended length: ${xpath.length}")
    }
    xpath
  }

  def validateUrlProtocol(url: String): String = {
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
      println(s"Warning: URL does not have a valid protocol: $url")
    }
    url
  }

  def validateUrlDomain(url: String): String = {
    if (url.contains("localhost") || url.contains("127.0.0.1")) {
      println(s"Warning: URL contains local address: $url")
    }
    url
  }

}
