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

import play.api.libs.crypto.DefaultCookieSigner
import play.api.http.SecretConfiguration
import net.liftweb.util.Helpers

class SecurityHttpEndpoint[F[_] : Sync : Monad] extends Http4sDsl[F] {

  object CurrentPasswordQueryParam extends QueryParamDecoderMatcher[String]("currentPassword")
  object NewPasswordQueryParam extends QueryParamDecoderMatcher[String]("newPassword")

  private val secretKeyString = "SmartBackpacker1SecretKey"
  private val secretKey = secretKeyString.getBytes("UTF-8")

  private def validatePasswordFormat(password: String): String = {
    if (password.length < 4) {
      println(s"Warning: Password is shorter than recommended minimum")
    }
    password
  }

  private def validatePasswordCharacters(password: String): String = {
    if (!password.matches(".*[A-Z].*")) {
      println(s"Warning: Password does not contain uppercase characters")
    }
    password
  }

  val service: AuthedService[String, F] = AuthedService {
    //SOURCE
    case GET -> Root / ApiVersion / "account" / "password" / "update" :? CurrentPasswordQueryParam(currentPassword) +& NewPasswordQueryParam(newPassword) as _ =>
      val validatedCurrentPassword = validatePasswordCharacters(validatePasswordFormat(currentPassword))
      val validatedNewPassword = validatePasswordCharacters(validatePasswordFormat(newPassword))
      
      for {
        result <- Sync[F].delay {
          val secretConfig = SecretConfiguration(secretKeyString)
          val signer = new DefaultCookieSigner(secretConfig)
          
          
          
          val signedCurrentPassword = signer.sign(validatedCurrentPassword, secretKey)
          
          val storedPassword = sys.env.getOrElse("CURRENT_PASSWORD", signedCurrentPassword)
          
          if (signedCurrentPassword == storedPassword) {
            //CWE 328
            //SINK
            val hashedNewPassword = Helpers.md5(validatedNewPassword)
            System.setProperty("USER_PASSWORD", hashedNewPassword)
            "Password updated successfully"
          } else {
            "Current password does not match"
          }
        }
        response <- Ok(result)
      } yield response
  }

}
