package io.github.deivid22srk.turnipspace.virtual

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.deivid22srk.turnipspace.R

/**
 * Placeholder component the system launches in place of the plugin activity.
 * Under a working engine hook (see PluginInstrumentation) this class is never
 * instantiated — the framework receives the plugin activity instead. If the
 * hook failed (hidden-API restrictions on newer Android builds) this activity
 * renders a clear diagnostic screen instead of crashing.
 */
open class StubActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render(
            title = getString(R.string.launch_failed),
            body = getString(R.string.engine_hook_failed),
        )
    }

    protected fun render(title: String, body: String) {
        val pad = (resources.displayMetrics.density * 24).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#12131A"))
            setPadding(pad, pad, pad, pad)
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
        }
        val bodyView = TextView(this).apply {
            text = body
            setTextColor(Color.parseColor("#B8C0CC"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, pad, 0, pad)
        }
        val close = Button(this).apply {
            text = getString(R.string.ok)
            setOnClickListener { finish() }
        }
        root.addView(titleView)
        root.addView(bodyView)
        root.addView(close)
        setContentView(root)
    }
}

/** Extra stubs: ActivityManager needs distinct installed components per stack. */
class StubActivity1 : StubActivity()
class StubActivity2 : StubActivity()
class StubActivity3 : StubActivity()
class StubActivity4 : StubActivity()
