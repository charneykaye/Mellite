/*
 *  DocumentElementsFrame.scala
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

package de.sciss
package mellite
package gui

import impl.document.{ElementsFrameImpl => Impl}
import lucre.stm
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.expr.Expr
import de.sciss.lucre.swing.View

object DocumentElementsFrame {
  /** Creates a new frame for document elements.
    *
    * @param workspace        the workspace whose root to display
    * @param name             optional window name
    * @param isWorkspaceRoot  if `true`, closes the workspace when the window closes; if `false` does nothing
    *                         upon closing the window
    */
  def apply[S <: Sys[S], S1 <: Sys[S1]](name: Option[Expr[S1, String]], isWorkspaceRoot: Boolean)
                        (implicit tx: S#Tx, workspace: Workspace[S], cursor: stm.Cursor[S],
                         bridge: S#Tx => S1#Tx): DocumentElementsFrame[S] =
    Impl(nameOpt = name, isWorkspaceRoot = isWorkspaceRoot)
}

trait DocumentElementsFrame[S <: Sys[S]] extends lucre.swing.Window[S] {
  // def folder: FolderView[S]
}