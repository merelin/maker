package starling.gui.pages

import starling.gui.{StarlingIcons, StarlingServerContext}
import starling.pivot.PivotEdits

case class UserStatsPage(pivotPageState:PivotPageState) extends AbstractPivotPage(pivotPageState) {
  def text = "User Stats"
  override val icon = StarlingIcons.im("/icons/16x16_stats.png")

  def selfPage(pps:PivotPageState, edits:PivotEdits) = copy(pivotPageState = pps)
  def dataRequest(pageBuildingContext:StarlingServerContext) = {
    pageBuildingContext.server.userStatsPivot(pivotPageState.pivotFieldParams)
  }
}