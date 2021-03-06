/*
 *  EnsembleView.scala
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

import de.sciss.desktop.UndoManager
import de.sciss.lucre.stm
import de.sciss.lucre.swing.View
import de.sciss.lucre.synth.Sys
import de.sciss.synth.proc.{Ensemble, Transport, Workspace}
import impl.document.{EnsembleViewImpl => Impl}

object EnsembleView {
  def apply[S <: Sys[S]](ensemble: Ensemble[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                                cursor: stm.Cursor[S], undoManager: UndoManager): EnsembleView[S] =
    Impl(ensemble)
}
trait EnsembleView[S <: Sys[S]] extends View.Editable[S] with ViewHasWorkspace[S] {
  def folderView: FolderView[S]

  def ensemble(implicit tx: S#Tx): Ensemble[S]

  def transport: Transport[S]
}