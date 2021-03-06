/*
 *  GainImpl.scala
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
package impl
package tracktool

import java.awt.Cursor
import javax.swing.Icon
import javax.swing.undo.UndoableEdit

import de.sciss.lucre.expr.{DoubleObj, SpanLikeObj}
import de.sciss.lucre.stm.Obj
import de.sciss.lucre.synth.Sys
import de.sciss.lucre.{expr, stm}
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.synth
import de.sciss.synth.proc.{ObjKeys, Timeline}

final class GainImpl[S <: Sys[S]](protected val canvas: TimelineProcCanvas[S])
  extends BasicRegion[S, TrackTool.Gain] {

  import TrackTool.Gain

  def defaultCursor: Cursor = Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)
  val name                  = "Gain"
  val icon: Icon            = GUI.iconNormal(Shapes.Gain) // ToolsImpl.getIcon("vresize")

  protected def dialog(): Option[Gain] = None // not yet supported

  override protected def dragStarted(d: Drag): Boolean =
    d.currentEvent.getY != d.firstEvent.getY

  protected def dragToParam(d: Drag): Gain = {
    val dy = d.firstEvent.getY - d.currentEvent.getY
    // use 0.1 dB per pixel. eventually we could use modifier keys...
    import synth._
    val factor = (dy.toFloat / 10).dbamp
    Gain(factor)
  }

  protected def commitObj(drag: Gain)(span: SpanLikeObj[S], obj: Obj[S], timeline: Timeline[S])
                         (implicit tx: S#Tx, cursor: stm.Cursor[S]): Option[UndoableEdit] = {
    import drag.factor
    // ProcActions.adjustGain(obj, drag.factor)
    // val imp = ExprImplicits[S]
    if (factor == 1f) None else {
      import expr.Ops._
      val newGain: DoubleObj[S] = obj.attr.$[DoubleObj](ObjKeys.attrGain) match {
        case Some(DoubleObj.Var(vr)) => vr() * factor.toDouble
        case other =>
          other.fold(1.0)(_.value) * factor
      }
      import de.sciss.equal.Implicits._
      val newGainOpt = if (newGain === DoubleObj.newConst[S](1.0)) None else Some(newGain)
      implicit val doubleTpe = DoubleObj
      val edit = EditAttrMap.expr[S, Double, DoubleObj](s"Adjust $name", obj, ObjKeys.attrGain, newGainOpt)
//      { ex =>
//        DoubleObj.newVar(ex))
//      }
      Some(edit)
    }
  }
}
