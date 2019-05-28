package fuselang

import scala.util.{Try, Success, Failure}
import java.nio.file.Path

import common.{Errors, CompilerError}

import typechecker.TypeChecker
import passes.{BoundsChecker, RewriteView}

import Utils._

object Compiler {

  def compileStringWithError(prog: String, c: CmdConfig = emptyConf) = {
    val ast = FuseParser.parse(prog)
    TypeChecker.typeCheck(ast)
    BoundsChecker.check(ast);
    val rast = RewriteView.rewriteProg(ast)
    c.backend.emit(rast, c.toCommonConfig())
  }

  // Outputs red text to the console
  def red(txt: String): String = {
    Console.RED + txt + Console.RESET
  }

  def compileString(prog: String, c: CmdConfig): Either[String, String] = Try {
    compileStringWithError(prog, c)
  } match {
    case Success(out) => Right(out)
    case Failure(f: Errors.TypeError) =>
      Left(s"[${red("Type error")}] ${f.getMessage}")
    case Failure(f: Errors.ParserError) =>
      Left(s"[${red("Parsing error")}] ${f.getMessage}")
    case Failure(f: CompilerError.Impossible) =>
      Left(s"[${red("Impossible")}] ${f.getMessage}. " +
        "This should never trigger. Please report this as a bug.")
    case Failure(f: RuntimeException) =>
      Left(s"[${red("Error")}] ${f.getMessage}")
    case Failure(f) => Left(f.getMessage)
  }

  def compileStringToFile(prog: String, c: CmdConfig, out: String): Either[String, Path] = {
    import java.nio.file.{Files, Paths, StandardOpenOption}

    compileString(prog, c).map(p => {
      Files.write(
        Paths.get(out),
        p.toCharArray.map(_.toByte),
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE)
    })
  }

}
