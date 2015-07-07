/*
 *  ProcCanvasImpl.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2015 Hanns Holger Rutz. All rights reserved.
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
package timeline

import de.sciss.audiowidgets.impl.TimelineCanvasImpl
import de.sciss.lucre.synth.Sys
import TrackTool.EmptyRubber

trait ProcCanvasImpl[S <: Sys[S]] extends TimelineCanvasImpl with TimelineProcCanvas[S] {
  final val trackTools = TrackTools[S](this)

  import TrackTools._

  //  private var _toolState = Option.empty[Any]
  //  final protected def toolState = _toolState

  protected var toolState: Option[Any]
  protected var rubberState: TrackTool.DragRubber = EmptyRubber

  private val toolListener: TrackTool.Listener = {
    // case TrackTool.DragBegin =>
    case TrackTool.DragCancel =>
      log(s"Drag cancel $toolState")
      if (toolState.isDefined) {
        toolState   = None
        repaint()
      } else if (rubberState.isValid) {
        rubberState = EmptyRubber
        repaint()
      }

    case TrackTool.DragEnd =>
      log(s"Drag end $toolState")
      toolState.fold[Unit] {
        if (rubberState.isValid) {
          rubberState = EmptyRubber
          repaint()
        }
      } { state =>
        toolState   = None
        rubberState = EmptyRubber
        commitToolChanges(state)
        repaint()
      }

    case TrackTool.DragAdjust(value) =>
      // log(s"Drag adjust $value")
      val some = Some(value)
      if (toolState != some) {
        toolState = some
        repaint()
      }

    case TrackTool.Adjust(state) =>
      log(s"Tool commit $state")
      toolState = None
      commitToolChanges(state)
      repaint()

    case state: TrackTool.DragRubber =>
      log(s"Tool rubber $state")
      rubberState = state
      repaint()
  }

  trackTools.addListener {
    case ToolChanged(change) =>
      change.before.removeListener(toolListener)
      change.now   .addListener   (toolListener)
    case VisualBoostChanged   (change) => repaint()
    case FadeViewModeChanged  (change) => repaint()
    case RegionViewModeChanged(change) => repaint()
  }
  trackTools.currentTool.addListener(toolListener)

  private val selectionListener: SelectionModel.Listener[S, TimelineObjView[S]] = {
    case SelectionModel.Update(added, removed) =>
      canvasComponent.repaint() // XXX TODO: dirty rectangle optimization
  }

  override protected def componentShown(): Unit = {
    super.componentShown()
    selectionModel.addListener(selectionListener)
  }

  override protected def componentHidden(): Unit = {
    super.componentHidden()
    selectionModel.removeListener(selectionListener)
  }

  protected def commitToolChanges(value: Any): Unit
}