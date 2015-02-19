/*
 *  NuagesFolderViewImpl.scala
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
package document

import de.sciss.desktop.UndoManager
import de.sciss.icons.raphael
import de.sciss.lucre.stm
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{View, Window, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.nuages.{Nuages, NuagesView, ScissProcs}
import de.sciss.swingplus.Separator

import scala.swing.Swing._
import scala.swing.event.ButtonClicked
import scala.swing.{BoxPanel, Button, Component, Orientation}

object NuagesFolderViewImpl {
  def apply[S <: Sys[S]](nuagesObj: Nuages.Obj[S])(implicit tx: S#Tx, workspace: Workspace[S],
                                                   cursor: stm.Cursor[S], undoManager: UndoManager): Impl[S] = {
    de.sciss.impuls2015.Populate.registerActions[S]()
    val nuages  = nuagesObj.elem.peer
    val folder  = FolderView(nuages.folder)
    val folder1 = new FolderFrameImpl.ViewImpl[S](folder)
    folder1.init()
    val nCfg    = Nuages.Config()
    val sCfg    = ScissProcs.Config()
    de.sciss.impuls2015.Settings(nCfg, sCfg)
    val res     = new Impl(tx.newHandle(nuagesObj), folder1, nCfg, sCfg)
    deferTx {
      res.guiInit()
    }
    res
  }

  final class Impl[S <: Sys[S]](nuagesH: stm.Source[S#Tx, Nuages.Obj[S]],
                                val view: FolderFrameImpl.ViewImpl[S],
                                nConfig: Nuages.Config, sConfig: ScissProcs.Config)
                               (implicit val undoManager: UndoManager, val workspace: Workspace[S],
                                val cursor: stm.Cursor[S])
    extends ComponentHolder[Component] with View.Editable[S] { impl =>

    def guiInit(): Unit = {
      val ggPower = new Button("Live!") {
        listenTo(this)
        reactions += {
          case ButtonClicked(_) => impl.cursor.step { implicit tx => openLive() }
        }
      }
      val shpPower = raphael.Shapes.Power _
      ggPower.icon          = GUI.iconNormal  (shpPower)
      ggPower.disabledIcon  = GUI.iconDisabled(shpPower)

      val ggClear = Button("Clear Timeline") {
        cursor.step { implicit tx =>
          val tl = nuagesH().elem.peer.timeline.elem.peer
          tl.modifiableOption.foreach { tlMod =>
            tlMod.clear() // XXX TODO -- use undo manager?
          }
        }
      }

      val ggPopulate = Button("Populate") {
        cursor.step { implicit tx =>
          de.sciss.impuls2015.Populate(nuagesH().elem.peer, nConfig, sConfig)
        }
      }

      component = new BoxPanel(Orientation.Vertical) {
        contents += view.component
        contents += Separator()
        contents += VStrut(2)
        contents += new BoxPanel(Orientation.Horizontal) {
          contents += ggPower
          contents += HStrut(32)
          contents += ggClear
          contents += ggPopulate
        }
      }
    }

    def dispose()(implicit tx: S#Tx): Unit = view.dispose()

    private def openLive()(implicit tx: S#Tx): Option[Window[S]] = {
      import de.sciss.mellite.Mellite.auralSystem
      val n     = nuagesH().elem.peer
      val frame = new WindowImpl[S] {
        val view = NuagesView(n, nConfig, sConfig)
        override val undecorated = true

        override protected def initGUI(): Unit = window.component match {
          case w: scala.swing.Window => view.installFullScreenKey(w)
          case _ =>
        }

        override protected def checkClose(): Boolean =
          cursor.step { implicit tx => !view.panel.transport.isPlaying }
      }
      frame.init()
      Some(frame)
    }
  }
}
