/*
Copyright 2012 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.scalding

import java.io.Serializable
import java.lang.reflect.{Type, ParameterizedType}

import cascading.pipe.Pipe
import cascading.tap.SinkMode
import cascading.tuple.{Tuple, TupleEntry, Fields}

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala._
import com.fasterxml.jackson.databind.ObjectMapper

/**
* This Source writes out the TupleEntry as a simple JSON object, using the field
* names as keys and the string representation of the values.
*
* TODO: it would be nice to have a way to add read/write transformations to pipes
* that doesn't require extending the sources and overriding methods.
*/
case class JsonLine(p: String, fields: Fields = Fields.ALL,
  override val sinkMode: SinkMode = SinkMode.REPLACE)
  extends FixedPathSource(p) with TextLineScheme {

  import Dsl._
  import JsonLine._

  override def transformForWrite(pipe : Pipe) = pipe.mapTo(fields -> 'json) {
    t: TupleEntry => mapper.writeValueAsString(TupleConverter.ToMap(t))
  }

  override def transformForRead(pipe : Pipe) = pipe.mapTo('line -> fields) {
    line : String =>
      val fs: Map[String, AnyRef] = mapper.readValue(line, mapTypeReference)
      val values = (0 until fields.size).map {
        i : Int => fs.getOrElse(fields.get(i).toString, null)
      }
      new cascading.tuple.Tuple(values : _*)
  }
  override def toString = "JsonLine(" + p + ", " + fields.toString + ")"
}

/**
 * TODO: at the next binary incompatible version remove the AbstractFunction2/scala.Serializable jank which
 * was added to get mima to not report binary errors
 */
object JsonLine extends scala.runtime.AbstractFunction3[String,Fields,SinkMode,JsonLine] with Serializable with scala.Serializable {

  val mapTypeReference = typeReference[Map[String, AnyRef]]

  private [this] def typeReference[T: Manifest] = new TypeReference[T] {
    override def getType = typeFromManifest(manifest[T])
  }

  private [this] def typeFromManifest(m: Manifest[_]): Type = {
    if (m.typeArguments.isEmpty) { m.erasure }
    else new ParameterizedType {
      def getRawType = m.erasure

      def getActualTypeArguments = m.typeArguments.map(typeFromManifest).toArray

      def getOwnerType = null
    }
  }

  val mapper = new ObjectMapper()
  mapper.registerModule(DefaultScalaModule)

}