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

import au.com.cba.omnia.maestro.core.codec.Decode

/**
  * Creates a custom Decode for the given thrift struct where the scala code is generated by Scrooge.
  * 
  * The Decode will look like this:
  * {{{
  * Decode((source, position) => {
  *   def decodeVals(vs: List[Val], position: Int): DecodeResult[(DecodeSource, Int, Customer2)] =
  *     if (vs.length < 7)
  *       DecodeError(ValDecodeSource(vs), position, NotEnoughInput(7, "au.com.cba.omnia.maestro.test.thrift.scrooge.Customer2"))
  *     else {
  *       val fields = vs.take(7).toArray
  *       val result = Customer2.apply(
  *         if (fields(0).isInstanceOf[StringVal])
  *           fields(0).asInstanceOf[StringVal].v
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(0 - position)), position + 0, ValTypeMismatch(fields(0), "String")),
  *         if (fields(1).isInstanceOf[StringVal])
  *           fields(1).asInstanceOf[StringVal].v
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(1 - position)), position + 1, ValTypeMismatch(fields(1), "String")),
  *         if (fields(2).isInstanceOf[StringVal])
  *           fields(2).asInstanceOf[StringVal].v
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(2 - position)), position + 2, ValTypeMismatch(fields(2), "String")),
  *         if (fields(3).isInstanceOf[StringVal])
  *           fields(3).asInstanceOf[StringVal].v
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(3 - position)), position + 3, ValTypeMismatch(fields(3), "String")),
  *         if (fields(4).isInstanceOf[NoneVal.type])
  *           Option.empty[String]
  *         else
  *           if (fields(4).isInstanceOf[StringVal])
  *           Option(fields(4).asInstanceOf[StringVal].v)
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(4 - position)), position + 4, ValTypeMismatch(fields(4), "Option[String]")),
  *         if (fields(5).isInstanceOf[NoneVal.type])
  *           Option.empty[Int]
  *         else
  *           if (fields(5).isInstanceOf[IntVal])
  *           Option(fields(5).asInstanceOf[IntVal].v)
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(5 - position)), position + 5, ValTypeMismatch(fields(5), "Option[Int]")),
  *         if (fields(6).isInstanceOf[StringVal])
  *           fields(6).asInstanceOf[StringVal].v
  *         else
  *           return DecodeError(ValDecodeSource(vs.drop(6 - position)), position + 6, ValTypeMismatch(fields(6), "String")))
  *       DecodeOk((ValDecodeSource(vs.drop(7)), position + 7, result))
  *     }
  * 
  *   def decodeUnknowns(vs: List[String], position: Int): DecodeResult[(DecodeSource, Int, Customer2)] =
  *     if (vs.length < 7)
  *       DecodeError(UnknownDecodeSource(vs), position, NotEnoughInput(7, "au.com.cba.omnia.maestro.test.thrift.scrooge.Customer2"))
  *     else {
  *       val fields = vs.take(7).toArray
  *       var index = -1
  *       var tag = ""
  *       try {
  *         val result = Customer2.apply(
  *           fields(0),
  *           fields(1),
  *           fields(2),
  *           fields(3),
  *           Option(fields(4)),
  *           {
  *             tag = "Option[Int]"
  *             index = 5
  *             if (fields(index).isEmpty)
  *               Option.empty[Int]
  *             else
  *               Option(fields(index).toInt)
  *           },
  *           fields(6))
  *         DecodeOk((UnknownDecodeSource(vs.drop(7)), position + 7, result))
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
object ScroogeDecodeMacro {
  def impl[A <: ThriftStruct: c.WeakTypeTag](c: Context): c.Expr[Decode[A]] = {
    import c.universe._

    val stringType = weakTypeOf[String]

    val typ       = weakTypeOf[A]
    val typeName  = typ.toString
    val companion = typ.typeSymbol.companionSymbol
    val members   = Inspect.indexed[A](c)
    val size      = members.length

    def decodeValSource(xs: List[(MethodSymbol, Int)]) = q"""
      def decodeVals(vs: List[Val], position: Int): DecodeResult[(DecodeSource, Int, $typ)] = {
        if (vs.length < $size) {
          DecodeError(ValDecodeSource(vs), position, NotEnoughInput($size, $typeName))
        } else {
          val fields = vs.take($size).toArray
          val result = ${build(decodeVals(xs))}
          DecodeOk((ValDecodeSource(vs.drop($size)), position + $size, result))
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
            val result = ${build(decodeUnknowns(xs))}
            DecodeOk((UnknownDecodeSource(vs.drop($size)), position + $size, result))
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

    def build(args: List[Tree]) = Apply(Select(Ident(companion), newTermName("apply")), args)

    def decodeVals(xs: List[(MethodSymbol, Int)]): List[Tree] = xs.map { case (x, i) =>
      val index = i - 1

      MacroUtils.optional(c)(x.returnType).map { param =>
        val tag     = s"Option[$param]"
        val typeVal = newTypeName(param + "Val")
        q"""
          if (fields($index).isInstanceOf[NoneVal.type])
            Option.empty[$param]
          else if (fields($index).isInstanceOf[$typeVal])
            Option(fields($index).asInstanceOf[$typeVal].v)
          else
            return DecodeError(ValDecodeSource(vs.drop($index - position)), position + $index, ValTypeMismatch(fields($index), $tag))
        """
      } getOrElse {
        val tag     = x.returnType.toString
        val typeVal = newTypeName(x.returnType + "Val")
        q"""
          if (fields($index).isInstanceOf[$typeVal])
            fields($index).asInstanceOf[$typeVal].v
          else
            return DecodeError(ValDecodeSource(vs.drop($index - position)), position + $index, ValTypeMismatch(fields($index), $tag))
        """
      }
    }

    def decodeUnknowns(xs: List[(MethodSymbol, Int)]): List[Tree] = xs.map { case (x, i) =>
      MacroUtils.optional(c)(x.returnType).map { param =>
        if (param == stringType) q"Option(fields(${i - 1}))"
        else {
          val method = newTermName("to" + param)
          val tag    = s"Option[$param]"
          q"""{
            tag   = $tag
            index = ${i - 1}
            if (fields(index).isEmpty) Option.empty[$param]
            else                       Option(fields(index).$method)
          }"""
        }
      } getOrElse {
        if (x.returnType == stringType) q"fields(${i - 1})"
        else {
          val method = newTermName("to" + x.returnType)
          val tag    = x.returnType.toString
          q"""{
            tag   = $tag
            index = ${i - 1}

            fields(index).$method
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
        import au.com.cba.omnia.maestro.core.codec.{DecodeSource, ValDecodeSource, UnknownDecodeSource, DecodeOk, DecodeError, DecodeResult, ParseError, ValTypeMismatch, NotEnoughInput}

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
