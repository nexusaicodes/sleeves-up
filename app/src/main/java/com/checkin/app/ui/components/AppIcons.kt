package com.checkin.app.ui.components

import com.adevinta.spark.icons.ArrowLeft
import com.adevinta.spark.icons.Book
import com.adevinta.spark.icons.ChevronVerticalLeft
import com.adevinta.spark.icons.ChevronVerticalRight
import com.adevinta.spark.icons.LeboncoinIcons
import com.adevinta.spark.icons.ManWalkingOutline
import com.adevinta.spark.icons.ShareExternal
import com.adevinta.spark.icons.SparkIcon

/**
 * The app's directional Spark icons, each flipped under a right-to-left layout by [mirroredInRtl].
 *
 * Held here, outside any composable, because each [mirroredInRtl] call returns a new icon that is
 * never equal to the last. "Start" and "end" name the reading direction: [ChevronEnd] points right
 * under English and left under Arabic. Every other icon is read at its call site, since it reads
 * the same either way.
 */
object AppIcons {
    val Back: SparkIcon = LeboncoinIcons.ArrowLeft.mirroredInRtl()
    val ChevronStart: SparkIcon = LeboncoinIcons.ChevronVerticalLeft.mirroredInRtl()
    val ChevronEnd: SparkIcon = LeboncoinIcons.ChevronVerticalRight.mirroredInRtl()
    val Walking: SparkIcon = LeboncoinIcons.ManWalkingOutline.mirroredInRtl()
    val Book: SparkIcon = LeboncoinIcons.Book.mirroredInRtl()
    val OpenElsewhere: SparkIcon = LeboncoinIcons.ShareExternal.mirroredInRtl()
}
