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
package rhttpc.transport.json4s

import org.json4s.JsonAST.{JObject, JString}
import org.json4s._

import scala.reflect.ClassTag

class CustomSerializerWithTypeHints[T: Manifest, JV <: JValue: ClassTag](
  ser: Formats => (PartialFunction[JV, T], PartialFunction[T, JV]))
  extends CustomSubTypesSerializer[T, JObject](implicit formats => {
  val (deserialize, serialize) = ser(formats)
  (
    {
      case JObject(_ :: ("value", jValue: JV) :: Nil) if deserialize.isDefinedAt(jValue) =>
        deserialize(jValue)
    },
    {
      case obj: T if serialize.isDefinedAt(obj) =>
        JObject(
          formats.typeHintFieldName -> JString(obj.getClass.getName),
          "value" -> serialize(obj)
        )
    }
  )
})