package starling.pivot.view.swing

import starling.pivot._
import java.awt.{Graphics, Component => AWTComp, KeyboardFocusManager}
import collection.mutable.ListBuffer
import model._
import scala.Array
import javax.swing._
import scala.swing.Swing._
import starling.pivot.EditableCellState._
import collection.immutable.Map
import scalaz.Scalaz._


import PivotTableType._

case class TreeFieldInformation(field:Field, selectedValue:Any)

class PivotJTableRowModel(helper: PivotJTableModelHelper, var rowHeaderData0:Array[Array[AxisCell]],
                          var extraFormatInfo:ExtraFormatInfo) extends PivotJTableModel {
  type CellType = AxisCell

  lazy val extraLine = helper.extraLine
  lazy val fieldState = helper.fieldState
  lazy val keyFields = helper.keyFields
  lazy val pagePivotEdits = helper.pagePivotEdits
  lazy val updateEdits = helper.updateEdits
  lazy val editableInfo = helper.editableInfo
  lazy val fieldInfo = helper.fieldInfo
  lazy val popupListView = helper.popupListView
  lazy val popupMenu = helper.popupMenu

  val numOriginalRows = rowHeaderData0.length
  private val numCols = rowHeaderData0(0).length
  private val addedRows0 = new ListBuffer[Array[AxisCell]]

  private val blankRow = Array.fill(numCols)(AxisCell.BlankAddedCell).zipWithIndex.map{case (ac,c) => {
    if (c < fieldState.rowFields.size) {
      val f = field(c)
      if (keyFields.contains(f)) {
        ac
      } else {
        ac.copy(editable = false)
      }
    } else {
      ac.copy(editable = false)
    }
  }}
  if (extraLine) {
    addedRows0 += blankRow
  }

  def validState = !overrideMap.exists{case (_,ac) => ac.state.invalid}

  def addRow() {
    updateStateOfLastRow()
    addedRows0 += blankRow
  }

  def removeRows(rows:List[Int]) {
    rows.foreach(r => {
      addedRows0.remove(r - numOriginalRows)
      overrideMap = overrideMap.filterNot{case ((r0,_),_) => (r == r0)}
      val keysToUpdate = overrideMap.filter{case ((r0,_),_) => (r0 > r)}
      overrideMap --= keysToUpdate.keySet
      overrideMap ++= keysToUpdate.map{case((r0,c0),v) => (r0-1,c0) -> v}
    })
  }

  private def updateStateOfLastRow() {
    val r = getRowCount - 1
    for (c <- (0 until numCols)) {
      val ac = getValueAt(r, c)
      val newState = if (ac.state.invalid) {
        ac.state
      } else {
        CellState.AddedCellState
      }
      overrideMap((r,c)) = ac.copy(overrideState = Some(newState))
    }
  }

  override def revert(table:PivotJTable) = {
    super.revert(table)
    if (addedRows0.size > 1) {
      val selectedColumn = table.getSelectedColumn
      val r = getRowCount - 1
      addedRows0.clear()
      addedRows0 += blankRow
      fireTableRowsDeleted(r, r)
      table.setSelectedCells(List((r-1, selectedColumn)))
      true
    } else {
      false
    }
  }

  def overrideEdits = overrideMap.toMap

  def getRowCount = {rowHeaderData0.length + addedRows0.length}
  val getColumnCount = numCols
  def getValueAt(rowIndex:Int, columnIndex:Int):AxisCell = {
    def v = {
      if (rowIndex < numOriginalRows) {
        rowHeaderData0(rowIndex)(columnIndex)
      } else {
        addedRows0(0)(columnIndex)
      }
    }
    overrideMap.getOrElse((rowIndex, columnIndex), v)
  }
  def paintTable(g:Graphics, table:JTable, rendererPane:CellRendererPane, rMin:Int, rMax:Int, cMin:Int, cMax:Int) {
    PivotTableUI.rowHeaderPaintGrid(g, table, rMin, rMax, cMin, cMax, getColumnCount - 1)
    PivotTableUI.rowHeaderPaintCells(g, table, rendererPane, rMin, rMax, cMin, cMax)
  }
  def rowHeader(row:Int,col:Int) = true
  def collapseOrExpand(row:Int, col:Int, pivotTableView:PivotTableView) {
    val path = (0 to col).map(colIndex => {getValueAt(row, colIndex).value}).toList
    pivotTableView.collapseOrExpandRow(path)
  }

  def duplicateRows(rows:Set[(Int,Int)]) {
    rows.foreach{case (duplicateRow, duplicateOfRow) => {
      (0 until getColumnCount).foreach(col => {
        if (keyFields.contains(field(col))) {
          val current = getValueAt(duplicateRow, col)
          val currentState = current.state
          overrideMap((duplicateRow, col)) = current.copy(overrideState = Some(currentState.copy(duplicate = true)), duplicateText = Some("This row is a duplicate of row " + duplicateOfRow))
        }
      })
    }}
  }

  def resetDuplicateRows(rows:Set[Int]) {
    overrideMap = overrideMap.map{element => {
      val ((r,c),v) = element
      if (rows.contains(r)) {
        val currentState = v.state
        ((r,c) -> v.copy(overrideState = Some(currentState.copy(duplicate = false)), duplicateText = None))
      } else {
        element
      }
    }}
  }

  override def rowHeaderStrategySelection(row:Int, col:Int) = {
    val selectedValue:AxisCell = getValueAt(row, col)
    val field = selectedValue.value.field
    if (fieldInfo.treeFields.contains(field)) {
      Some(TreeFieldInformation(field, selectedValue.value.value.value))
    } else {
      None
    }
  }

  def rowHeaderFieldToValues(rowIndex: Int, columnIndex: Option[Int] = None) = {
    val rowHeaderCells = rowHeaderData0(rowIndex) |> (row => columnIndex.fold(ci => row.take(ci + 1), row))

    rowHeaderCells.map(_.selection).toMap.filterNot{case (f,_) => f == Field.NullField}
  }

  def key(rowIndex:Int, columnIndex:Int): Map[Field, SomeSelection] = {
    val filterFieldToValues = fieldState.filtersInTheFilterArea.collect{case (f, SomeSelection(v)) => (f, SomeSelection(v))}
    val rowFilters = fieldState.rowFilters
    val columnFilters = fieldState.columnFilters

    (rowHeaderFieldToValues(rowIndex, Some(columnIndex)) ++ filterFieldToValues ++ rowFilters ++ columnFilters) //& keyFields
  }

  def deleteKey(rowIndex:Int): Map[Field, SomeSelection] = {
      val filterFieldToValues = fieldState.filtersInTheFilterArea.collect{case (f, SomeSelection(v)) => (f, SomeSelection(v))}
      val columnFilters = fieldState.columnFilters

      (rowHeaderFieldToValues(rowIndex, Some(numCols-1)) ++ filterFieldToValues ++ columnFilters) //& keyFields
    }

  def rowsHaveBeenDeletedByTheMainTable(rows:List[Int]) {
    val cRange = (0 until numCols)
    rows.foreach(r => {
      cRange.foreach(c => {
        val ac = getValueAt(r,c)
        overrideMap((r,c)) = ac.copy(overrideState = Some(CellState.DeletedCellState))
      })
      fireTableRowsUpdated(r,r)
    })
  }

  def deletedRowsHaveBeenReset(rows:List[Int]) {
    val cRange = (0 until numCols)
    rows.foreach(r => {
      cRange.foreach(c => {
        overrideMap -= ((r,c))
      })
      fireTableRowsUpdated(r,r)
    })
  }

  override def deleteCells(cells:List[(Int,Int)], currentEdits:PivotEdits, fireChange:Boolean) = {
    var deleteEdits = currentEdits
    def isAdded(r:Int, ac:AxisCell):Boolean = {
      (ac.state.state == Added) || (r >= numOriginalRows) || ac.value.childKey.value.isInstanceOf[NewRowValue]
    }
    val (newRowCells, normalCells) = cells.partition{case (r,c) => isAdded(r, getValueAt(r,c))}

    newRowCells.foreach{case (r,c) => {
      val value = getValueAt(r,c)
      value.value.childKey.value match {
        case NewRowValue(rowIndex) => {
          deleteEdits = deleteEdits.withNewAmended(rowIndex, value.value.field, None)
          overrideMap((r,c)) = value.copy(label = "", longLabel = "", overrideState = Some(CellState.DeletedCellState))
        }
        case _ => {
          val k = ((r,c))
          overrideMap(k) = overrideMap(k).copy(label = "", longLabel = "", overrideState = Some(CellState.AddedCellState))
        }
      }
    }}

    val cRange = (0 until numCols)
    val normalRowsToDelete = normalCells.map(_._1).distinct
    normalRowsToDelete.foreach(r => {
      cRange.foreach(c => {
        overrideMap -= ((r,c))
        val ac = getValueAt(r,c)
        overrideMap((r,c)) = ac.copy(overrideState = Some(CellState.DeletedCellState))
      })
    })

    helper.tellMainTableAboutDeletedRows(normalRowsToDelete)

    if (fireChange) {
      deleteEdits = helper.deleteEditedRowsIfRequired(deleteEdits)
      deleteEdits = helper.removeAddedRowsIfBlank(deleteEdits)
    }

    maybeUpdate(fireChange, currentEdits, deleteEdits, cells.map(_._1))
    deleteEdits
  }

  private def maybeUpdate(fireChange:Boolean, currentEdits:PivotEdits, otherEdits:PivotEdits, rowsUpdated:List[Int]) {
    if (fireChange) {
      if (otherEdits != currentEdits) {
        val newEdits = helper.allEdits(otherEdits)
        updateEdits(newEdits, RowHeader)
      } else {
        helper.tableUpdated(rowsUpdated)
      }
    }
  }

  override def resetCells(cells:List[(Int,Int)], currentEdits:PivotEdits, fireChange:Boolean) = {
    var resetEdits = currentEdits
    val deletedRowsToReset = new ListBuffer[Int]
    cells.foreach{case (r,c) => {
      val k = (r,c)
      if (overrideMap.contains(k)) {
        if (overrideMap(k).state.state == Deleted) {
          deletedRowsToReset += r
        }
      }
      if (r >= numOriginalRows) {
        overrideMap(k) = overrideMap(k).copy(label = "", longLabel = "", overrideState = Some(CellState.AddedCellState))
      } else {
        overrideMap -= k
      }
      resetEdits = resetEdits.remove(getValueAt(r,c).value.pivotEdits)
    }}
    deletedRowsToReset.distinct.foreach(r => {
      (0 until numCols).foreach(c => {
        overrideMap -= ((r,c))
      })
    })

    helper.tellMainTableAboutDeletedRowsThatHaveBeenReset(deletedRowsToReset.toList)

    maybeUpdate(fireChange, currentEdits, resetEdits, cells.map(_._1))
    resetEdits
  }

  override def isCellEditable(rowIndex:Int, columnIndex:Int) = {
    val v = getValueAt(rowIndex, columnIndex)
    v.editable && v.shown && (v.state.state != Deleted)
  }

  def field(col:Int) = fieldState.rowFields(col)

  override def parser(row:Int, col:Int) = {
    val rowHeaderField = field(col)
    editableInfo.get.fieldToParser(rowHeaderField)
  }

  override def setValuesAt(values:List[TableValue], currentEdits:PivotEdits, fireChange:Boolean) = {
    var anyResetEdits = currentEdits
    values.foreach(tv => {
      val r = tv.row
      val c = tv.column
      val value = tv.value

      val currentValue = getValueAt(r,c)

      if (currentValue.editable && !currentValue.hidden) {
        val stringValue = value.asInstanceOf[String].trim
        val pars = parser(r, c)

        val (newValue,newLabel,error) =  try {
          val (v,t) = pars.parse(stringValue, extraFormatInfo)
          (Some(v), t, false)
        } catch {
          case e:Exception => (None, stringValue, true)
        }
        val stateToUse = if (r < numOriginalRows && currentValue.state.state != Added) Edited else Added

        val k = (r,c)
        overrideMap -= k
        val originalCell = getValueAt(r,c)
        val originalLabel =  originalCell.value.value.originalValue match {
          case None => {
            if (originalCell.label.nonEmpty) {
              originalCell.label
            } else {
              "sfkjfhxcjkvuivyruvhrzzasaf$%£$££"
            }
          }
          case Some(ov) => fieldInfo.fieldToFormatter(field(c)).format(ov, extraFormatInfo).text
        }
        if (originalLabel == newLabel) {
          anyResetEdits = resetCells(List(k), anyResetEdits, false)
        } else {
          if (error) {
            overrideMap(k) = originalCell.copy(label = stringValue, longLabel = stringValue, overrideState = Some(CellState(stateToUse, true)))
          } else {
            overrideMap(k) = originalCell.copy(label = newLabel, longLabel = newLabel, overrideState = Some(CellState(stateToUse)), overrideValue = newValue)
          }
        }
      }
    })
    maybeUpdate(fireChange, currentEdits, anyResetEdits, values.map(_.row))
    anyResetEdits
  }

  override def setValueAt(value:AnyRef, rowIndex:Int, columnIndex:Int) {
    val s = value.asInstanceOf[String].trim
    if (s.isEmpty) {
      val currentCell = getValueAt(rowIndex, columnIndex)
      if (currentCell.state.state != AddedBlank) {
        deleteCells(List((rowIndex, columnIndex)), pagePivotEdits, true)
      }
    } else {
      setValuesAt(List(TableValue(s, rowIndex, columnIndex)), pagePivotEdits, true)
    }
  }

  def acceptableValues(r:Int, c:Int):Set[String] = {
    val rowHeaderField = field(c)
    fieldState.fieldSelection(rowHeaderField) match {
      case None => {
        val parser = editableInfo.get.fieldToParser(rowHeaderField)
        parser.acceptableValues
      }
      case Some(filteredValues) => {
        val formatter = fieldInfo.fieldToFormatter.getOrElse(rowHeaderField, DefaultPivotFormatter)
        filteredValues.map(v => formatter.format(v, extraFormatInfo).text)
      }
    }
  }

  def textTyped(textField:JTextField, cellEditor:CellEditor, r:Int, c:Int, focusOwner:Option[AWTComp], tableFrom:PivotJTable) {
    // Using a zero row index here as it doesn't really matter as long as it is the correct column.
    val vals = acceptableValues(0, c)
    if (vals.nonEmpty) {
      val t = textField.getText.toLowerCase
      val filteredVals = vals.filter(_.toLowerCase.startsWith(t))
      val sortedVals = {
        if (filteredVals.nonEmpty) {
          filteredVals.toList.sortWith(_.toLowerCase < _.toLowerCase)
        } else {
          vals.toList.sortWith(_.toLowerCase < _.toLowerCase)
        }
      }
      val currentListData = popupListView.listData.toList
      if (currentListData != sortedVals) {
        popupListView.listData = sortedVals
      }
      if (!popupMenu.isShowing) {
        val selectionAtTimeOfPopup = textField.getSelectedText
        val caretPositionAtTimeOfPopup = textField.getCaretPosition

        popupMenu.setMinimumSize(helper.viewScrollPane.preferredSize)
        popupMenu.show(textField, cellEditor, tableFrom, textField, 0, textField.getSize().height-1)
        focusOwner.map(_.requestFocusInWindow())

        onEDT(onEDT({
          val currentSelection = textField.getSelectedText
          if (selectionAtTimeOfPopup == null && currentSelection != null) {
            // It is likely we are on windows and we need to do something funky so that we don't select the whole text field.
            val l = textField.getText.length()
            val posToUse = if (caretPositionAtTimeOfPopup <= l) caretPositionAtTimeOfPopup else l
            textField.setCaretPosition(posToUse)
          }
        }))
      }
    }
  }
  def finishedEditing() {
    popupMenu setVisible false
    popupListView.peer.clearSelection()
  }
  def popupShowing = popupMenu.isShowing
  def focusPopup() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager.focusNextComponent(popupMenu)
    popupListView.requestFocusInWindow()
    popupListView.selectIndices(0)
  }
  override def selectPopupValueIfOnlyOneShowing(row:Int, col:Int) {
    if (popupShowing && popupListView.listData.size == 1) {
      val valueToUse = popupListView.listData.head
      setValueAt(valueToUse, row, col)
    }
  }
  override def singlePopupValue(row:Int, col:Int) = {
    if (popupShowing && popupListView.listData.size == 1) {
      val valueToUse = popupListView.listData.head
      Some(valueToUse)
    } else {
      None
    }
  }
}
