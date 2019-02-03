package fuselang

import scala.util.parsing.combinator._
//import scala.util.parsing.input.Positional

import Syntax._

private class FuseParser extends RegexParsers with PackratParsers {
  type P[T] = PackratParser[T]

  override protected val whiteSpace = """(\s|#.*|(/\*((\*[^/])|[^*])*\*/))+""".r

  val reservedTerms = Set("for", "if", "bit", "bool", "true", "false", "bank")

   // General parser combinators
  def braces[T](parser: P[T]): P[T] = "{" ~> parser <~ "}"
  def brackets[T](parser: P[T]): P[T] = "[" ~> parser <~ "]"
  def parens[T](parser: P[T]): P[T] = "(" ~> parser <~ ")"
  def angular[T](parser: P[T]): P[T] = "<" ~> parser <~ ">"

  // General syntax components
  lazy val iden: P[Id] = positioned {
    "" ~> "[a-z_][a-zA-Z0-9_]*".r ^^ { v => Id(v) }
  }

  // Atoms
  lazy val number = "(-)?[0-9]+".r ^^ { n => n.toInt }
  lazy val float = "(-)?[0-9]+.[0-9]+".r ^^ { n => n.toFloat }
  lazy val boolean = "true" ^^ { _ => true } | "false" ^^ { _ => false }
  lazy val eaa: P[Expr] = positioned {
    iden ~ rep1("[" ~> expr <~ "]") ^^ { case id ~ idxs => EAA(id, idxs) }
  }
  lazy val atom: P[Expr] = positioned {
    eaa |
    float ^^ { case f => EFloat(f) } |
    number ^^ { case n => EInt(n) } |
    boolean ^^ { case b => EBool(b) } |
    iden ^^ { case id => EVar(id) }
  }

  // Binops
  lazy val mulOps: P[Op2] = positioned {
    "/" ^^ { _ => OpDiv } |
    "*" ^^ { _ => OpDiv }
  }
  lazy val addOps: P[Op2] = positioned {
    "+" ^^ { _ => OpAdd } |
    "-" ^^ { _ => OpSub }
  }
  lazy val eqOps: P[Op2] = positioned {
    "==" ^^ { _ => OpEq } |
    "!=" ^^ { _ => OpNeq } |
    ">=" ^^ { _ => OpGte } |
    "<=" ^^ { _ => OpLte } |
    ">"  ^^ { _ => OpGt } |
    "<"  ^^ { _ => OpLt }
  }

  // Expressions
  lazy val binMul: P[Expr] = positioned {
    atom ~ mulOps ~ binMul ^^ { case l ~ op ~ r => EBinop(op, l, r)} |
    atom
  }
  lazy val binAdd: P[Expr] = positioned {
    binMul ~ addOps ~ binAdd ^^ { case l ~ op ~ r => EBinop(op, l, r)} |
    binMul
  }
  lazy val binEq: P[Expr] = positioned {
    binAdd ~ eqOps ~ binEq ^^ { case l ~ op ~ r => EBinop(op, l, r)} |
    binAdd
  }
  lazy val expr = positioned (binEq | parens(binEq))

  // Types
  lazy val typIdx: P[(Int, Int)] =
    brackets(number ~ "bank" ~ number) ^^ { case n ~ _ ~ b => (n, b) } |
    brackets(number)^^ { n => (n, 1) }
  lazy val atyp: P[Type] =
    "float" ^^ { _ => TFloat } |
    "bool" ^^ { _ => TBool } |
    "bit" ~> angular(number) ^^ { case s => TSizedInt(s) }
  lazy val typ: P[Type] =
    atyp ~ rep1(typIdx) ^^ { case t ~ dims => TArray(t, dims) } |
    atyp

  // Commands
  lazy val block: P[Command] =
    braces(cmd) |
    "{" ~ "}" ^^ { case _ ~ _ => CEmpty }
  lazy val cfor: P[Command] = positioned {
    "for" ~ "(" ~ "let" ~> iden ~ "=" ~ number ~ ".." ~ number ~ ")" ~ block ^^ {
      case id ~ _ ~ s ~ _ ~ e ~ _ ~ par => CFor(id, CRange(s, e, 1), par, CReducer(CEmpty))
    } |
    "for" ~ "(" ~ "let" ~> iden ~ "=" ~ number ~ ".." ~ number ~ ")" ~ "unroll" ~ number ~ block ^^ {
      case id ~ _ ~ s ~ _ ~ e ~ _ ~ _ ~ u ~ par => CFor(id, CRange(s, e, u), par, CReducer(CEmpty))
    }
  }
  lazy val acmd: P[Command] = positioned {
    cfor |
    "decl" ~> iden ~ ":" ~ typ ^^ { case id ~ _ ~ typ => CDecl(id, typ)} |
    expr ~ ":=" ~ expr ^^ { case l ~ _ ~ r => CUpdate(l, r) } |
    "let" ~> iden ~ "=" ~ expr ^^ { case id ~ _ ~ exp => CLet(id, exp) } |
    "if" ~> parens(expr) ~ block ^^ { case cond ~ cons => CIf(cond, cons) } |
    expr ^^ { case e => CExpr(e) }
  }

  lazy val cmd: P[Command] = positioned {
    acmd ~ ";" ~ cmd ^^ { case c1 ~ _ ~ c2 => CSeq(c1, c2) } |
    acmd ~ ";" ~ "---" ~ cmd ^^ { case c1 ~ _ ~ _ ~ c2 => CSeq(c1, CSeq(CRefreshBanks, c2)) } |
    acmd <~ ";" |
    acmd
  }

}

object FuseParser {
  private val parser = new FuseParser()
  import parser._

  def parse(str: String) = parseAll(cmd, str) match {
    case Success(res, _) => res
    case res => throw Errors.ParserError(s"$res")
  }
}
