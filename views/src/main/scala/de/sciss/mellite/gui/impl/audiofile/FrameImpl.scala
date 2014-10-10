/*
 *  FrameImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2014 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui
package impl
package audiofile

import de.sciss.lucre.stm
import de.sciss.synth.proc
import proc.{AudioGraphemeElem, Obj}
import de.sciss.file._
import de.sciss.lucre.synth.Sys
import proc.Implicits._

object FrameImpl {
  def apply[S <: Sys[S]](obj: Obj.T[S, AudioGraphemeElem])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S]): AudioFileFrame[S] = {
    implicit val aural = Mellite.auralSystem
    val afv       = AudioFileView(obj)
    val name      = obj.attr.name
    val file      = obj.elem.peer.value.artifact
    val fileName  = file.base
    val title     = if (name == fileName) name else s"$name - $fileName"
    val res       = new Impl(/* doc, */ view = afv, name = name, _file = file, title0 = title)
    res.init()
    res
  }

  private final class Impl[S <: Sys[S]](/* val document: Workspace[S], */ val view: AudioFileView[S], name: String,
                                        _file: File, title0: String)
                                       (implicit cursor: stm.Cursor[S])
    extends WindowImpl[S](title0)
    with AudioFileFrame[S] {

    override protected def initGUI(): Unit = windowFile = Some(_file)
  }
}