package fr.azks.clavier

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/** Provider-owned surfaces: never inspect, log, copy or persist their content. */
@Composable
fun AutofillStrip(views: List<View>) {
    if (views.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        views.forEach { view -> key(view) {
            AndroidView(factory = { context -> FrameLayout(context).apply {
                (view.parent as? ViewGroup)?.removeView(view)
                addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            } }, modifier = Modifier.height(48.dp), onRelease = { it.removeAllViews() }, update = {})
        } }
    }
}
