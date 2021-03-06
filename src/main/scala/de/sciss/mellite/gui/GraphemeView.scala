/*
 *  GraphemeView.scala
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

import de.sciss.audiowidgets.TimelineModel
import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.impl.grapheme.{GraphemeViewImpl => Impl}
import de.sciss.synth.proc.{Grapheme, Workspace}

import scala.swing.{Action, Component}

object GraphemeView {
  def apply[S <: Sys[S]](group: Grapheme[S])
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         undoManager: UndoManager): GraphemeView[S] =
    Impl[S](group)
}
trait GraphemeView[S <: stm.Sys[S]] extends ViewHasWorkspace[S] with View.Editable[S] {
  def timelineModel: TimelineModel
  
  def selectionModel  : GraphemeObjView.SelectionModel[S]

  def graphemeH: stm.Source[S#Tx, Grapheme[S]]
  def grapheme(implicit tx: S#Tx): Grapheme[S]

  def canvasComponent: Component

  // def transportView: TransportView[S]

  // ---- GUI actions ----
  def actionDelete            : Action
  def actionClearSpan         : Action
  def actionRemoveSpan        : Action
  def actionMoveObjectToCursor: Action
}