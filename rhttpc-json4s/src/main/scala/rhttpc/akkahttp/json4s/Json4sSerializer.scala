/*
 * Copyright 2015 the original author or authors.
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
package rhttpc.akkahttp.json4s

import org.json4s.Formats
import org.json4s.native.Serialization
import rhttpc.transport.{Deserializer, Serializer}

import scala.util.Try

class Json4sSerializer[PubMsg <: AnyRef](implicit formats: Formats) extends Serializer[PubMsg] {
  override def serialize(msg: PubMsg): String = {
    Serialization.write(msg)(formats)
  }
}

class Json4sDeserializer[SubMsg](implicit subMsgManifest: Manifest[SubMsg],
                                 formats: Formats) extends Deserializer[SubMsg] {
  override def deserialize(value: String): Try[SubMsg] = {
    Try(Serialization.read[SubMsg](value))
  }
}