/*
 *  ElementActions.scala
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

import de.sciss.synth.proc.Artifact.Location
import de.sciss.synth.proc.Grapheme
import de.sciss.synth.io.AudioFileSpec
import de.sciss.file._
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.expr.{Double => DoubleEx, Long => LongEx}

object ElementActions {
  def addAudioFile[S <: Sys[S]](folder: Folder[S], index: Int, loc: Location.Modifiable[S],
                                f: File, spec: AudioFileSpec)
                               (implicit tx: S#Tx): Element.AudioGrapheme[S] = {
    val offset    = LongEx  .newVar[S](LongEx  .newConst(0L))
    val gain      = DoubleEx.newVar[S](DoubleEx.newConst(1.0))
    val artifact  = loc.add(f)
    val audio     = Grapheme.Elem.Audio(artifact, spec, offset, gain)
    val name      = f.base
    val elem      = Element.AudioGrapheme(name, audio)
    if (index == -1) folder.addLast(elem) else folder.insert(index, elem)
    elem
  }

  def findAudioFile[S <: Sys[S]](root: Folder[S], file: File)
                                (implicit tx: S#Tx): Option[Element.AudioGrapheme[S]] = {
    def loop(folder: Folder[S]): Option[Element.AudioGrapheme[S]] =
      folder.iterator.flatMap {
        case a: Element.AudioGrapheme[S] if a.entity.value.artifact == file => Some(a)
        case f: Element.Folder[S] => loop(f.entity)
        case _ => None
      } .toList.headOption

    loop(root)
  }
}