/*
 *  ElementView.scala
 *  (Mellite)
 *
 *  Copyright (c) 2012-2013 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU General Public License
 *  as published by the Free Software Foundation; either
 *  version 2, june 1991 of the License, or (at your option) any later version.
 *
 *  This software is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public
 *  License (gpl.txt) along with this software; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.mellite
package gui

import de.sciss.synth.proc.Sys
import de.sciss.lucre.stm
import scalaswingcontrib.tree.Tree
import swing.{Swing, BoxPanel, Orientation, Component}
import collection.immutable.{IndexedSeq => IIdxSeq}
import javax.swing.tree.DefaultTreeCellRenderer
import Swing._

object ElementView {
  import java.lang.{String => _String}
  import scala.{Int => _Int, Double => _Double}

  private[gui] def apply[S <: Sys[S]](element: Element[S])(implicit tx: S#Tx): ElementView[S] = {
    val name = element.name.value
    element match {
      case elem @ Element.Int(ex) =>
        val value = ex.value
        new Int.Impl(tx.newHandle(elem), name, value)
      case elem @ Element.Double(ex) =>
        val value = ex.value
        new Double.Impl(tx.newHandle(elem), name, value)
      case elem @ Element.String(ex) =>
        val value = ex.value
        new String.Impl(tx.newHandle(elem), name, value)
      case elem @ Element.Group(g) =>
        val children = g.iterator.map(apply(_)(tx)).toIndexedSeq
        new Group.Impl(tx.newHandle(elem), name, children)
      case elem @ Element.ProcGroup(_) =>
        new ProcGroup.Impl(tx.newHandle(elem), name)
    }
  }

  object String {
    private object Comp extends BoxPanel(Orientation.Horizontal) {
      val key = new DefaultTreeCellRenderer
      key.setLeafIcon(null)
      val value = new DefaultTreeCellRenderer
      value.setLeafIcon(null)
      background = null
      contents += Component.wrap(key)
      contents += HStrut(8)
      contents += Component.wrap(value)
    }

    private[ElementView] final class Impl[S <: Sys[S]](val element: stm.Source[S#Tx, Element.String[S]],
                                                       var name: _String, var value: _String)
      extends String[S] with ElementView.Impl[S] {

      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = {
        Comp.key  .getTreeCellRendererComponent(tree.peer, name,  info.isSelected, false, true, info.row, info.hasFocus)
        Comp.value.getTreeCellRendererComponent(tree.peer, value, info.isSelected, false, true, info.row, info.hasFocus)
        Comp
      }
      def prefix = "String"
    }
  }
  sealed trait String[S <: Sys[S]] extends ElementView[S] {
    def element: stm.Source[S#Tx, Element.String[S]]
  }

  object Int {
    private[ElementView] final class Impl[S <: Sys[S]](val element: stm.Source[S#Tx, Element.Int[S]],
                                                      var name: _String, var value: _Int)
      extends Int[S] with ElementView.Impl[S] {

      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = ??? // cmpBlank // XXX TODO
      def prefix = "Int"
    }
  }
  sealed trait Int[S <: Sys[S]] extends ElementView[S] {
    def element: stm.Source[S#Tx, Element.Int[S]]
  }

  object Double {
    private[ElementView] final class Impl[S <: Sys[S]](val element: stm.Source[S#Tx, Element.Double[S]],
                                                       var name: _String, var value: _Double)
      extends Double[S] with ElementView.Impl[S] {

      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = ??? // cmpBlank // XXX TODO
      def prefix = "Double"
    }
  }
  sealed trait Double[S <: Sys[S]] extends ElementView[S] {
    def element: stm.Source[S#Tx, Element.Double[S]]
  }

  sealed trait GroupLike[S <: Sys[S]] extends Renderer {
    var children: IIdxSeq[ElementView[S]]
  }

  object Group {
    private[ElementView] final class Impl[S <: Sys[S]](val element: stm.Source[S#Tx, Element.Group[S]],
                                                       var name: _String, var children: IIdxSeq[ElementView[S]])
      extends Group[S] with ElementView.Impl[S] {

      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = {
        ???
  //      // never show the leaf icon, always a folder icon. for empty folders, show the icon as if the folder is open
  //      cmpGroupJ.getTreeCellRendererComponent(tree.peer, name, info.isSelected, info.isExpanded || info.isLeaf,
  //        false /* info.isLeaf */, info.row, info.hasFocus)
  //      cmpGroup
      }

      def prefix = "Group"
    }
  }
  sealed trait Group[S <: Sys[S]] extends ElementView[S] with GroupLike[S] {
    def element: stm.Source[S#Tx, Element.Group[S]]
  }

  object ProcGroup {
    private[ElementView] final class Impl[S <: Sys[S]](val element: stm.Source[S#Tx, Element.ProcGroup[S]],
                                                       var name: _String)
      extends ProcGroup[S] with ElementView.Impl[S] {

      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = {
        ???
  //      cmpLabelJ.getTreeCellRendererComponent(tree.peer, name, info.isSelected, false, true, info.row, info.hasFocus)
  //      cmpLabel
      }
      def prefix = "ProcGroup"
    }
  }
  sealed trait ProcGroup[S <: Sys[S]] extends ElementView[S] {
    def element: stm.Source[S#Tx, Element.ProcGroup[S]]
  }

  object Root {
    private[gui] def apply[S <: Sys[S]](group: Elements[S])(implicit tx: S#Tx): Root[S] = {
      val children = group.iterator.map(ElementView(_)(tx)).toIndexedSeq
      new Impl(children)
    }

    private final class Impl[S <: Sys[S]](var children: IIdxSeq[ElementView[S]])
      extends Renderer {
      def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component = ??? // cmpBlank
    }
  }
  sealed trait Root[S <: Sys[S]] extends GroupLike[S]

  private sealed trait Impl[S <: Sys[S]] extends ElementView[S] {
    protected def prefix: _String
    override def toString = s"ElementView.$prefix(name = $name)"
  }

  sealed trait Renderer {
    private[gui] def componentFor(tree: Tree[_], info: Tree.Renderer.CellInfo): Component
  }
}
sealed trait ElementView[S <: Sys[S]] extends ElementView.Renderer {
  def element: stm.Source[S#Tx, Element[S]]
  def name: String
}