/*
 *  FolderViewImpl.scala
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
package document

import java.io.File
import javax.swing.event.{CellEditorListener, ChangeEvent}
import javax.swing.undo.UndoableEdit
import javax.swing.{CellEditor, DropMode}

import de.sciss.desktop.UndoManager
import de.sciss.lucre.artifact.Artifact
import de.sciss.lucre.expr.StringObj
import de.sciss.lucre.stm
import de.sciss.lucre.stm.{Disposable, Obj}
import de.sciss.lucre.swing.TreeTableView.ModelUpdate
import de.sciss.lucre.swing.impl.ComponentHolder
import de.sciss.lucre.swing.{TreeTableView, deferTx}
import de.sciss.lucre.synth.Sys
import de.sciss.mellite.gui.edit.EditAttrMap
import de.sciss.model.impl.ModelImpl
import de.sciss.synth.proc.{Folder, ObjKeys, Workspace}
import de.sciss.treetable.j.{DefaultTreeTableCellEditor, TreeTableCellEditor}
import de.sciss.treetable.{TreeTableCellRenderer, TreeTableSelectionChanged}

import scala.collection.breakOut
import scala.collection.immutable.{IndexedSeq => Vec}
import scala.swing.Component
import scala.util.control.NonFatal

object FolderViewImpl {
  def apply[S <: Sys[S]](root0: Folder[S])
                        (implicit tx: S#Tx, workspace: Workspace[S],
                         cursor: stm.Cursor[S], undoManager: UndoManager): FolderView[S] = {
    implicit val folderSer = Folder.serializer[S]

    new Impl[S] {
//      val mapViews: IdentifierMap[S#ID, S#Tx, ObjView[S]]               = tx.newInMemoryIDMap  // folder IDs to renderers
      val treeView: TreeTableView[S, Obj[S], Folder[S], ListObjView[S]] = TreeTableView(root0, TTHandler)

      deferTx {
        guiInit()
      }
    }
  }

  private abstract class Impl[S <: Sys[S]](implicit val undoManager: UndoManager, val workspace: Workspace[S],
                                           val cursor: stm.Cursor[S])
    extends ComponentHolder[Component]
    with FolderView[S]
    with ModelImpl[FolderView.Update[S]]
    with FolderViewTransferHandler[S] {

    view =>

    private type Data     = ListObjView[S]
    private type NodeView = TreeTableView.NodeView[S, Obj[S], Folder[S], Data]

    protected object TTHandler
      extends TreeTableView.Handler[S, Obj[S], Folder[S], ListObjView[S]] {

      def branchOption(node: Obj[S]): Option[Folder[S]] = node match {
        case fe: Folder[S] => Some(fe)
        case _ => None
      }

      def children(branch: Folder[S])(implicit tx: S#Tx): Iterator[Obj[S]] =
        branch.iterator

      private def updateObjectName(obj: Obj[S], nameOption: Option[String])(implicit tx: S#Tx): Boolean = {
        treeView.nodeView(obj).exists { nv =>
          val objView = nv.renderData
          deferTx {
            objView.nameOption = nameOption
          }
          true
        }
      }

      private type MUpdate = ModelUpdate[Obj[S], Folder[S]]

      // XXX TODO - d'oh this because ugly
      def observe(obj: Obj[S], dispatch: S#Tx => MUpdate => Unit)
                 (implicit tx: S#Tx): Disposable[S#Tx] = {
        val objH      = tx.newHandle(obj)
        val objReact  = obj.changed.react { implicit tx => _ =>
          // theoretically, we don't need to refresh the object,
          // because `treeView` uses an id-map for lookup.
          // however, there might be a problem with objects
          // created in the same transaction as the call to `observe`.
          //
          val obj = objH()
          val isDirty = treeView.nodeView(obj).exists { _ =>
            // val objView = nv.renderData
            false // XXX TODO RRR ELEM objView.isUpdateVisible(u1)
          }
          if (isDirty) dispatch(tx)(TreeTableView.NodeChanged(obj): MUpdate)
        }
        val attr      = obj.attr
        implicit val stringTpe = StringObj
        val nameView  = AttrCellView[S, String, StringObj](attr, ObjKeys.attrName)
        val attrReact = nameView.react { implicit tx => nameOpt =>
          val isDirty = updateObjectName(obj, nameOpt)
          if (isDirty) dispatch(tx)(TreeTableView.NodeChanged(obj): MUpdate)
        }

        val folderReact = obj match {
          case f: Folder[S] =>
            val res = f.changed.react { implicit tx => u2 =>
              u2.list.modifiableOption.foreach { folder =>
                val m = updateBranch(folder.asInstanceOf[Folder[S]] /* XXX TODO -- d'oh forgot this one */, u2.changes)
                m.foreach(dispatch(tx)(_))
              }
            }
            Some(res)

          case _ => None
        }

        new Disposable[S#Tx] {
          def dispose()(implicit tx: S#Tx): Unit = {
            objReact .dispose()
            attrReact.dispose()
            folderReact.foreach(_.dispose())
          }
        }
      }

      private def updateBranch(parent: Folder[S], changes: Vec[Folder.Change[S]]): Vec[MUpdate] =
        changes.flatMap {
          case Folder.Added  (idx, obj) => Vec(TreeTableView.NodeAdded  (parent, idx, obj): MUpdate)
          case Folder.Removed(idx, obj) => Vec(TreeTableView.NodeRemoved(parent, idx, obj): MUpdate)
        }

      private lazy val component = TreeTableCellRenderer.Default

      def renderer(tt: TreeTableView[S, Obj[S], Folder[S], Data], node: NodeView, row: Int, column: Int,
                   state: TreeTableCellRenderer.State): Component = {
        val data    = node.renderData
        val value1  = if (column == 0) data.name else "" // data.value
        // val value1  = if (value != {}) value else null
        val res = component.getRendererComponent(tt.treeTable, value1, row = row, column = column, state = state)
        if (column == 0) {
          if (row >= 0 && node.isLeaf) {
            try {
              // val node = t.getNode(row)
              component.icon = data.icon
            } catch {
              case NonFatal(_) => // XXX TODO -- currently NPE problems; seems renderer is called before tree expansion with node missing
            }
          }
          res // component
        } else {
          data.configureRenderer(component)
        }
      }

      private var editView    = Option.empty[ListObjView[S]]
      private var editColumn  = 0

      private lazy val defaultEditorJ = new javax.swing.JTextField
      private lazy val defaultEditor: TreeTableCellEditor = {
        val res = new DefaultTreeTableCellEditor(defaultEditorJ)
        res.addCellEditorListener(new CellEditorListener {
          def editingCanceled(e: ChangeEvent): Unit = ()
          def editingStopped (e: ChangeEvent): Unit = editView.foreach { objView =>
            editView = None
            val editOpt: Option[UndoableEdit] = cursor.step { implicit tx =>
              val text = defaultEditorJ.getText
              if (editColumn == 0) {
                val valueOpt: Option[StringObj[S]] /* Obj[S] */ = if (text.isEmpty || text.toLowerCase == "<unnamed>") None else {
                  val expr = StringObj.newConst[S](text)
                  // Some(Obj(StringObj(elem)))
                  Some(expr)
                }
                // val ed = EditAttrMap[S](s"Rename ${objView.prefix} Element", objView.obj(), ObjKeys.attrName, valueOpt)
                implicit val stringTpe = StringObj
                val ed = EditAttrMap.expr[S, String, StringObj](s"Rename ${objView.humanName} Element", objView.obj, ObjKeys.attrName,
                  valueOpt) // (StringObj[S](_))
                Some(ed)
              } else {
                objView.tryEdit(text)
              }
            }
            editOpt.foreach(undoManager.add)
          }
        })
        res
      }
      private lazy val defaultEditorC = Component.wrap(defaultEditorJ)

      def isEditable(data: Data, column: Int): Boolean = column == 0 || data.isEditable

      val columnNames: Vec[String] = Vector("Name", "Value")

      def editor(tt: TreeTableView[S, Obj[S], Folder[S], Data], node: NodeView, row: Int, column: Int,
                 selected: Boolean): (Component, CellEditor) = {
        val data    = node.renderData
        editView    = Some(data)
        editColumn  = column
        val value   = if (column == 0) data.name else data.value.toString
        defaultEditor.getTreeTableCellEditorComponent(tt.treeTable.peer, value, selected, row, column)
        (defaultEditorC, defaultEditor)
      }

      def data(node: Obj[S])(implicit tx: S#Tx): Data = ListObjView(node)
    }

    protected def treeView: TreeTableView[S, Obj[S], Folder[S], ListObjView[S]]

    def dispose()(implicit tx: S#Tx): Unit = {
      treeView.dispose()
    }

    def root: stm.Source[S#Tx, Folder[S]] = treeView.root

    protected def guiInit(): Unit = {
      val t = treeView.treeTable
      t.rootVisible = false

      val tabCM = t.peer.getColumnModel
      tabCM.getColumn(0).setPreferredWidth(176)
      tabCM.getColumn(1).setPreferredWidth(272)

      t.listenTo(t.selection)
      t.reactions += {
        case _: TreeTableSelectionChanged[_, _] =>  // this crappy untyped event doesn't help us at all
          // println(s"selection: $e")
          dispatch(FolderView.SelectionChanged(view, selection))
        // case e => println(s"other: $e")
      }
      t.showsRootHandles  = true
      // t.expandPath(TreeTable.Path(_model.root))
      t.dragEnabled       = true
      t.dropMode          = DropMode.ON_OR_INSERT_ROWS
      t.peer.setTransferHandler(FolderTransferHandler)
      val tc        = treeView.component
//      tc.peer.putClientProperty("styleId", "nofocus")
      tc.peer.putClientProperty("styleId", "undecorated")
      component     = tc

    }

    def selection: FolderView.Selection[S] = treeView.selection

    def insertionPoint(implicit tx: S#Tx): (Folder[S], Int) = treeView.insertionPoint

    def locations: Vec[ArtifactLocationObjView[S]] = selection.flatMap { nodeView =>
      nodeView.renderData match {
        case view: ArtifactLocationObjView[S] => Some(view)
        case _ => None
      }
    } (breakOut)

    def findLocation(f: File): Option[ActionArtifactLocation.QueryResult[S]] = {
      val locationsOk = locations.flatMap { view =>
        try {
          Artifact.relativize(view.directory, f)
          Some(view)
        } catch {
          case NonFatal(_) => None
        }
      } .headOption

      locationsOk match {
        case Some(loc)  => Some(Left(loc.objH))
        case _          =>
          //          val parent = selection.flatMap { nodeView =>
          //            nodeView.renderData match {
          //              case f: ObjView.Folder[S] => Some(f.obj)
          //              case _ => None
          //            }
          //          } .headOption
          ActionArtifactLocation.query[S](treeView.root, file = f /*, folder = parent */) // , window = Some(comp))
      }
    }
  }
}