//   Copyright 2014 Commonwealth Bank of Australia
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.

package au.com.cba.omnia.maestro.macros

import scala.reflect.macros.Context

import com.twitter.scrooge.ThriftStruct

import au.com.cba.omnia.humbug.HumbugThriftStruct

import au.com.cba.omnia.maestro.core.codec.Decode

/**
  * Creates a custom Decode for the given thrift struct where the scala code is generated by Humbug.
  * 
  * The Decode will look like this:
  * {{{
  * Decode((source, position) => {
  *   def decodeVals(vs: List[Val], position: Int): DecodeResult[(DecodeSource, Int, Types)] =
  *     if (vs.length < 7)
  *       DecodeError(ValDecodeSource(vs), position, NotEnoughInput(7, "au.com.cba.omnia.maestro.test.thrift.humbug.Types"))
  *     else {
  *       var index = -1
  *       var tag = ""
  *       val fields = vs.take(7).toArray
  *       try {
  *         val struct = new Types()
  *         {
  *           index = 0
  *           tag = "String"
  *           struct._1 = fields(0).asInstanceOf[StringVal].v
  *         }
  *         {
  *           index = 1
  *           tag = "Boolean"
  *           struct._2 = fields(1).asInstanceOf[BooleanVal].v
  *         }
  *         {
  *           index = 2
  *           tag = "Int"
  *           struct._3 = fields(2).asInstanceOf[IntVal].v
  *         }
  *         {
  *           index = 3
  *           tag = "Long"
  *           struct._4 = fields(3).asInstanceOf[LongVal].v
  *         }
  *         {
  *           index = 4
  *           tag = "Double"
  *           struct._5 = fields(4).asInstanceOf[DoubleVal].v
  *         }
  *         {
  *           index = 5
  *           tag = "Option[Int]"
  *           struct._6 = if (fields(5).isInstanceOf[NoneVal.type])
  *             Option.empty[Int]
  *           else
  *             Option(fields(5).asInstanceOf[IntVal].v)
  *         }
  *         {
  *           index = 6
  *           tag = "Option[String]"
  *           struct._7 = if (fields(6).isInstanceOf[NoneVal.type])
  *             Option.empty[String]
  *           else
  *             Option(fields(6).asInstanceOf[StringVal].v)
  *         }
  *         DecodeOk((ValDecodeSource(vs.drop(7)), position + 7, struct))
  *       } catch {
  *         case NonFatal((e @ _)) => DecodeError(ValDecodeSource(vs.drop(index - position)), position + index, ValTypeMismatch(fields(index), tag))
  *       }
  *     }
  *   def decodeUnknowns(vs: List[String], position: Int): DecodeResult[(DecodeSource, Int, Types)] =
  *     if (vs.length < 7)
  *       DecodeError(UnknownDecodeSource(vs), position, NotEnoughInput(7, "au.com.cba.omnia.maestro.test.thrift.humbug.Types"))
  *     else {
  *       val fields = vs.take(7).toArray
  *       var index = -1
  *       var tag = ""
  *       try {
  *         val struct = new Types()
  *         struct._1 = fields(0)
  *         {
  *           tag = "Boolean"
  *           index = 1
  *           struct._2 = fields(index).toBoolean
  *         }
  *         {
  *           tag = "Int"
  *           index = 2
  *           struct._3 = fields(index).toInt
  *         }
  *         {
  *           tag = "Long"
  *           index = 3
  *           struct._4 = fields(index).toLong
  *         }
  *         {
  *           tag = "Double"
  *           index = 4
  *           struct._5 = fields(index).toDouble
  *         }
  *         {
  *           tag = "Option[Int]"
  *           index = 5
  *           struct._6 = if (fields(index).isEmpty)
  *             Option.empty[Int]
  *           else
  *             Option(fields(index).toInt)
  *         }
  *         struct._7 = Option(fields(6))
  *         DecodeOk(scala.Tuple3(UnknownDecodeSource(vs.drop(7)), position + 7, struct))
  *       } catch {
  *         case NonFatal((e @ _)) => DecodeError(UnknownDecodeSource(vs.drop(index - position)), position + index, ParseError(fields(index), tag, That(e)))
  *       }
  *     }
  *   source match {
  *     case ValDecodeSource((vs @ _)) => decodeVals(vs, position)
  *     case UnknownDecodeSource((vs @ _)) => decodeUnknowns(vs, position)
  *   }
  * })
  * }}}
  */
object HumbugDecodeMacro {
  def impl[A <: ThriftStruct: c.WeakTypeTag](c: Context): c.Expr[Decode[A]] = {
    import c.universe._

    val stringType = weakTypeOf[String]

    val typ       = weakTypeOf[A]
    val typeName  = typ.toString
    val companion = typ.typeSymbol.companionSymbol
    val members   = Inspect.indexed[A](c)
    val size      = members.length
    val termName  = newTermName(typeName)

    def decodeValSource(xs: List[(MethodSymbol, Int)]) = q"""
      def decodeVals(vs: List[Val], position: Int): DecodeResult[(DecodeSource, Int, $typ)] = {
        if (vs.length < $size) {
          DecodeError(ValDecodeSource(vs), position, NotEnoughInput($size, $typeName))
        } else {
          var index = -1
          var tag   = ""
          val fields = vs.take($size).toArray

          try {
          val struct = new $typ()
          ..${decodeVals(xs)}
          DecodeOk((ValDecodeSource(vs.drop($size)), position + $size, struct))
          } catch {
            case NonFatal(e) => DecodeError(
              ValDecodeSource(vs.drop(index - position)),
              position + index,
              ValTypeMismatch(fields(index), tag))
          }
        }
      }
    """

    def decodeUnknownSource(xs: List[(MethodSymbol, Int)]) = q"""
      def decodeUnknowns(vs: List[String], position: Int): DecodeResult[(DecodeSource, Int, $typ)] = {
        if (vs.length < $size) {
          DecodeError(UnknownDecodeSource(vs), position, NotEnoughInput($size, $typeName))
        } else {
          val fields = vs.take($size).toArray
          var index = -1
          var tag   = "" 
          try {
            val struct = new $typ()
            ..${decodeUnknowns(xs)}
            DecodeOk((UnknownDecodeSource(vs.drop($size)), position + $size, struct))
          } catch {
            case NonFatal(e) => DecodeError(
              UnknownDecodeSource(vs.drop(index - position)),
              position + index,
              ParseError(fields(index), tag, That(e))
            )
          }
        }
      }
    """

    def decodeVals(xs: List[(MethodSymbol, Int)]): List[Tree] = xs.map { case (x, i) =>
      val index  = i - 1
      val setter = newTermName("_" + i)

      MacroUtils.optional(c)(x.returnType).map { param =>
        val tag     = s"Option[$param]"
        val typeVal = newTypeName(param + "Val")
        q"""
          index = $index
          tag   = $tag

          struct.$setter =
            if (fields($index).isInstanceOf[NoneVal.type]) Option.empty[$param]
            else Option(fields($index).asInstanceOf[$typeVal].v)
        """
      } getOrElse {
        val tag     = x.returnType.toString
        val typeVal = newTypeName(x.returnType + "Val")

        q"""
          index = $index
          tag   = $tag
          struct.$setter = fields($index).asInstanceOf[$typeVal].v
        """
      }
    }

    def decodeUnknowns(xs: List[(MethodSymbol, Int)]): List[Tree] = xs.map { case (x, i) =>
      val index  = i - 1
      val setter = newTermName("_" + i)

      MacroUtils.optional(c)(x.returnType).map { param =>
        if (param == stringType) q"struct.$setter = Option(fields($index))"
        else {
          val method = newTermName("to" + param)
          val tag    = s"Option[$param]"
          q"""{
            tag   = $tag
            index = $index

            struct.$setter = 
            if (fields(index).isEmpty) Option.empty[$param]
            else                       Option(fields(index).$method)
          }"""
        }
      } getOrElse {
        if (x.returnType == stringType)
          q"struct.$setter = fields($index)"
        else {
          val method = newTermName("to" + x.returnType)
          val tag    = x.returnType.toString

          q"""{
            tag   = $tag
            index = $index
            struct.$setter = fields(index).$method
          }"""
        }
      }
    }

    val combined = q"""
      import au.com.cba.omnia.maestro.core.codec.Decode
      Decode((source, position) => {
        import scala.util.control.NonFatal
        import scalaz.\&/.That
        import au.com.cba.omnia.maestro.core.data.{Val, BooleanVal, IntVal, LongVal, DoubleVal, StringVal, NoneVal}
        import au.com.cba.omnia.maestro.core.codec.{DecodeSource, ValDecodeSource, UnknownDecodeSource, DecodeOk, DecodeError, DecodeResult, ParseError, ValTypeMismatch, NotEnoughInput, Decode}
  
        ${decodeValSource(members)}
        ${decodeUnknownSource(members)}
  
        source match {
          case ValDecodeSource(vs)     => decodeVals(vs, position)
          case UnknownDecodeSource(vs) => decodeUnknowns(vs, position)
        }
      })
    """

    c.Expr[Decode[A]](combined)
  }
}
