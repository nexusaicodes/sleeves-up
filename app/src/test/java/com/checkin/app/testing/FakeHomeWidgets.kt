package com.checkin.app.testing

import com.checkin.app.widget.HomeWidgets
import com.checkin.app.widget.WidgetFace
import kotlinx.coroutines.flow.flowOf

class FakeHomeWidgets(var isPlaced: Boolean = true) : HomeWidgets {
    val shown = mutableListOf<WidgetFace>()
    var failNextShow = false

    override fun animationsOn() = flowOf(true)

    override fun placed() = isPlaced

    override fun show(face: WidgetFace): Boolean {
        if (failNextShow) {
            failNextShow = false
            error("launcher unavailable")
        }
        if (!isPlaced) return false
        shown += face
        return true
    }

    override fun canRequestPin() = false

    override fun requestPin() = false
}
