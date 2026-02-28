/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.common.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.util.Preferences
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

abstract class QkActivity : AppCompatActivity() {
    @Inject lateinit var prefs: Preferences

    protected val menu: Subject<Menu> = BehaviorSubject.create()

    protected val toolbar: Toolbar? get() = findViewById(R.id.toolbar)
    protected val toolbarTitle: TextView? get() = findViewById(R.id.toolbarTitle)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onNewIntent(intent)
        disableScreenshots(prefs.disableScreenshots.get())
    }

    override fun onResume() {
        super.onResume()
        disableScreenshots(prefs.disableScreenshots.get())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)
        setSupportActionBar(toolbar)
        title = title // The title may have been set before layout inflation
    }

    override fun setContentView(view: View?) {
        super.setContentView(view)
        setSupportActionBar(toolbar)
        title = title // The title may have been set before layout inflation
    }

    override fun setTitle(titleId: Int) {
        title = getString(titleId)
    }

    override fun setTitle(title: CharSequence?) {
        super.setTitle(title)
        toolbarTitle?.text = title
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val result = super.onCreateOptionsMenu(menu)
        if (menu != null) {
            this.menu.onNext(menu)
        }
        return result
    }

    protected open fun showBackButton(show: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(show)
    }

    /**
     * D-pad / flip-phone compatibility.
     *
     * DPAD_CENTER and ENTER simulate a click on the currently focused view so that every
     * button, list item, or interactive widget in the activity can be activated with hardware
     * keys without any touch input.
     *
     * Subclasses that need additional key handling (e.g. KEYCODE_MENU to open a drawer) should
     * call super.onKeyDown() first and only handle keys that return false here.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            // D-pad center or Enter: confirm / click the focused view
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                // Request long-press tracking so onKeyLongPress can fire
                event?.startTracking()
                currentFocus?.let { focused ->
                    // Only perform click if the view is a leaf interactive element, not a
                    // container, so we don't accidentally swallow events meant for an EditText.
                    if (focused.isClickable) {
                        focused.performClick()
                        return true
                    }
                }
            }

            // D-pad down from inside the toolbar (search bar, title, navigation icon, etc.):
            // jump to the first focusable view below the toolbar so the user can enter the
            // content area without needing to touch the screen.
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val focused = currentFocus
                if (focused != null && isDescendantOfToolbar(focused)) {
                    val tb = toolbar
                    if (tb != null) {
                        val below = firstFocusableBelowToolbar(tb)
                        if (below != null) {
                            below.requestFocus()
                            return true
                        }
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * D-pad / flip-phone: long-pressing D-pad center or Enter simulates a long-click on the
     * focused view.  This provides a keyboard equivalent for touch-only long-press actions such
     * as entering multi-select mode in conversation lists.
     */
    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            currentFocus?.let { focused ->
                if (focused.isLongClickable) {
                    focused.performLongClick()
                    return true
                }
            }
        }
        return super.onKeyLongPress(keyCode, event)
    }

    /** Returns true if [view] is a descendant of any Toolbar in the view hierarchy. */
    private fun isDescendantOfToolbar(view: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v is Toolbar) return true
            v = v.parent as? View
        }
        return false
    }

    /**
     * Walks the entire window hierarchy and returns the topmost focusable, visible, enabled
     * view whose on-screen Y coordinate is at or below the bottom edge of [tb].
     * Views that are themselves inside a Toolbar are excluded.
     */
    private fun firstFocusableBelowToolbar(tb: Toolbar): View? {
        val loc = IntArray(2)
        tb.getLocationInWindow(loc)
        val toolbarBottom = loc[1] + tb.height

        val candidates = mutableListOf<View>()
        collectFocusableViews(window.decorView, candidates)

        return candidates
            .filter { v ->
                v.getLocationInWindow(loc)
                loc[1] >= toolbarBottom && !isDescendantOfToolbar(v)
            }
            .minByOrNull { v ->
                v.getLocationInWindow(loc)
                loc[1]
            }
    }

    /** Recursively collects all focusable, visible, enabled views under [root]. */
    private fun collectFocusableViews(root: View, result: MutableList<View>) {
        if (root.visibility != View.VISIBLE) return
        if (root.isFocusable && root.isEnabled) result.add(root)
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) collectFocusableViews(root.getChildAt(i), result)
        }
    }

    private fun disableScreenshots(disableScreenshots: Boolean) {
        if (disableScreenshots) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

}