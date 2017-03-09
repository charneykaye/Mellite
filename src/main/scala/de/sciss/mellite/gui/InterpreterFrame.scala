/*
 *  InterpreterFrame.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2017 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.desktop
import de.sciss.lucre.stm.Sys
import de.sciss.mellite.gui.impl.interpreter.{InterpreterFrameImpl => Impl}
import de.sciss.synth.proc.Workspace

import scala.swing.event.Key

object InterpreterFrame {
  def apply(): InterpreterFrame = Impl()

  object Action extends swing.Action("Interpreter") {
    import desktop.KeyStrokes._
    accelerator = Some(menu1 + Key.R)

    def apply(): Unit = InterpreterFrame()
  }

  /** The content of this object is imported into the REPL */
  object Bindings {
    def document: Workspace[_ <: Sys[_]] =
      Application.documentHandler.activeDocument.getOrElse(sys.error("No document open"))

    def confluentDocument: Workspace.Confluent = document match {
      case cd: Workspace.Confluent => cd
      case _ => sys.error("Not a confluent document")
    }
  }
}
trait InterpreterFrame {
  def component: desktop.Window
}