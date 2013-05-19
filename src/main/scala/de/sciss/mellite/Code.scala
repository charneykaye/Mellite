package de.sciss.mellite

import de.sciss.serial.{Writable, DataInput, DataOutput, ImmutableSerializer}
import scala.annotation.switch
import impl.{CodeImpl => Impl, CodeImpl2 => Impl2}
import java.io.File
import scala.concurrent.Future
import de.sciss.processor.Processor

object Code {
  final case class CompilationFailed() extends Exception
  final case class CodeIncomplete()    extends Exception

  implicit def serializer: ImmutableSerializer[Code] = Impl.serializer

  def read(in: DataInput): Code = serializer.read(in)

  def apply(id: Int, source: String): Code = (id /* : @switch */ ) match {
    case FileTransform.id => FileTransform(source)
  }

  object FileTransform {
    final val id = 0
  }
  final case class FileTransform(source: String) extends Code {
    type In     = (File, File, Processor[Any, _] => Unit)
    type Out    = Future[Unit]
    def id      = FileTransform.id

    // def compile(): In => Out        = Impl.compile    [In, Out, FileTransform](this)
    def compileBody(): Future[Unit] = Impl2.compileBody[In, Out, FileTransform](this)

    def execute(in: In): Out = Impl2.execute[In, Out, FileTransform](this, in)

    def contextName = "File Transform"
  }
}
sealed trait Code extends Writable {
  /** The interfacing input type */
  type In
  /** The interfacing output type */
  type Out

  def id: Int
  def source: String
  def contextName: String

  // def compile(): In => Out
  def compileBody(): Future[Unit]

  def execute(in: In): Out // = compile()(in)

  def write(out: DataOutput) {
    Code.serializer.write(this, out)
  }
}