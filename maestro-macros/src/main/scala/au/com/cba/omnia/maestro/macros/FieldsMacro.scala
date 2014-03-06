package au.com.cba.omnia.maestro.macros

import au.com.cba.omnia.maestro.core.codec._
import au.com.cba.omnia.maestro.core.data._
import com.twitter.scrooge._

import scala.reflect.macros.Context
import scala.annotation.StaticAnnotation

class body(tree: Any) extends StaticAnnotation

object FieldsMacro {
  def impl[A <: ThriftStruct: c.WeakTypeTag](c: Context) = {
    import c.universe._

/*

     def selFieldImpl = {
      val field = c.macroApplication.symbol
      val bodyAnn = field.annotations.filter(_.tpe <:< typeOf[body]).head
      bodyAnn.scalaArgs.head
    }

    def mkObjectImpl(xs: c.Tree*) = {
      val kvps = xs.toList map { case q"${_}(${Literal(Constant(name: String))}).->[${_}]($value)" => name -> value }
      val fields = kvps map { case (k, v) => q"@body($v) def ${TermName(k)} = macro Macros.selFieldImpl" }
    }
*/

    val entries = Inspect.fields[A](c)
    val fields = entries.map({
      case (method, field) =>
        val name = Literal(Constant(method.name.toString))
        val typ = c.universe.weakTypeOf[A]
        val extract = Function(List(ValDef(Modifiers(Flag.PARAM), newTermName("x"), TypeTree(), EmptyTree)), Select(Ident(newTermName("x")), method.name))
        (method, field, q"""au.com.cba.omnia.maestro.core.data.Field[${typ}, ${method.returnType}]($name, ${extract})""")
    }).map({
      case (method, field, value) =>
        val n = newTermName(field)
        q"""def ${n} = $value"""
    })
    val r =q"class FieldsWrapper { ..$fields }; new FieldsWrapper {}"
    c.Expr(r)
  }
}