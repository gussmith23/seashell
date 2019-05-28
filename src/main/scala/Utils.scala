package fuselang

import java.io.File
import scribe.Level

import backend.{VivadoBackend, CppRunnable, Backend}

import common.{Config, CompilerError}

object Utils {

  sealed trait Mode
  case object Compile extends Mode
  case object Run extends Mode

  val emptyConf = CmdConfig(null)

  val validBackends = Set("vivado", "c++")

  def toBackend(str: String): Backend = str match {
    case "vivado" => VivadoBackend
    case "c++" => CppRunnable
    case b => throw CompilerError.Impossible("toBackend", s"Unknown backend $b")
  }

  case class CmdConfig(
    srcFile: File, // Required: Name of the source file
    kernelName: String = "kernel", // Name of the kernel to emit
    output: Option[String] = None, // Name of output file.
    backend: Backend = VivadoBackend, // Backend used for code generation
    mode: Mode = Compile, // Compilation mode
    compilerOpts: List[String] = List(),
    logLevel: Level = Level.Info,
    header: Boolean = false
  ) {
    def toCommonConfig(): Config = {
      Config(
        this.srcFile,
        this.kernelName,
        this.output,
        this.compilerOpts,
        this.logLevel,
        this.header)
    }
  }

  implicit class RichOption[A](opt: Option[A]) {
    def getOrThrow[T <: Throwable](except: T) = opt match {
      case Some(v) => v
      case None => throw except
    }
  }
}
