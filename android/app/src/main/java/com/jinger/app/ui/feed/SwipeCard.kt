package com.jinger.app.ui.feed

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class SwipeDirection { LEFT, RIGHT }

/**
 * Tinder-style draggable card. Rotates with horizontal drag and reports the direction once the card is
 * released past the threshold. [content] receives the horizontal progress (-1..1) to draw colour hints.
 */
@Composable
fun SwipeCard(
    key: Any,
    onSwiped: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (progress: Float) -> Unit,
) {
    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val threshold = screenWidthPx * 0.3f
    val offset = remember(key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val progress = (offset.value.x / threshold).coerceIn(-1f, 1f)

    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offset.value.x
                translationY = offset.value.y
                rotationZ = (offset.value.x / screenWidthPx) * 20f
            }
            .pointerInput(key) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        scope.launch { offset.snapTo(offset.value + drag) }
                    },
                    onDragEnd = {
                        val x = offset.value.x
                        if (abs(x) > threshold) {
                            val dir = if (x > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
                            val target = Offset(if (x > 0) screenWidthPx * 1.5f else -screenWidthPx * 1.5f, offset.value.y)
                            scope.launch {
                                offset.animateTo(target, tween(220))
                                onSwiped(dir)
                            }
                        } else {
                            scope.launch { offset.animateTo(Offset.Zero, spring(stiffness = 400f)) }
                        }
                    },
                )
            }
    ) {
        content(progress)
    }
}

private val Int.dp get() = androidx.compose.ui.unit.Dp(this.toFloat())
