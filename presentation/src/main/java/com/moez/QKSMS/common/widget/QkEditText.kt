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
package dev.octoshrimpy.quik.common.widget

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.google.android.mms.ContentType
import dev.octoshrimpy.quik.common.util.TextViewStyler
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.util.tryOrNull
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject


/**
 * Custom implementation of EditText to allow for dynamic text colors
 *
 * Beware of updating to extend AppCompatTextView, as this inexplicably breaks the view in
 * the contacts chip view
 */
class QkEditText @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null)
    : AppCompatEditText(context, attrs) {

    @Inject lateinit var textViewStyler: TextViewStyler

    val backspaces: Subject<Unit> = PublishSubject.create()
    val inputContentSelected: Subject<InputContentInfoCompat> = PublishSubject.create()
    var supportsInputContent: Boolean = false

    init {
        if (!isInEditMode) {
            appComponent.inject(this)
            textViewStyler.applyAttributes(this, attrs)
        } else {
            TextViewStyler.applyEditModeAttributes(this, attrs)
        }
    }

    /**
     * D-pad / flip-phone: intercept DPAD_DOWN and DPAD_UP on single-line fields BEFORE
     * Android's ArrowKeyMovementMethod or the IME can swallow them.
     *
     * We override dispatchKeyEvent (not onKeyDown) because EditText's movement method
     * and the soft-keyboard can both return true from onKeyDown before our override runs,
     * which silently swallows the navigation event.  dispatchKeyEvent is called first in
     * the View event chain, so we can reliably redirect focus here.
     *
     * For multi-line compose fields the default cursor-movement behaviour is preserved.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (maxLines == 1 && event.action == KeyEvent.ACTION_DOWN) {
            val direction = when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
                KeyEvent.KEYCODE_DPAD_UP   -> View.FOCUS_UP
                else                       -> -1
            }
            if (direction != -1) {
                val next = focusSearch(direction)
                if (next != null && next !== this) {
                    // requestFocus() on a RecyclerView or other container may return false
                    // if it has no currently-focused child.  Try to focus its first focusable
                    // descendant before giving up.
                    if (next.requestFocus()) return true
                    if (focusFirstDescendant(next)) return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Recursively finds and focuses the first enabled, focusable descendant of [root]. */
    private fun focusFirstDescendant(root: View): Boolean {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val child = root.getChildAt(i)
                if (child.visibility == View.VISIBLE && child.isFocusable && child.isEnabled) {
                    if (child.requestFocus()) return true
                }
                if (focusFirstDescendant(child)) return true
            }
        }
        return false
    }

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {

        val inputConnection = object : InputConnectionWrapper(super.onCreateInputConnection(editorInfo), true) {
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_DEL) {
                    backspaces.onNext(Unit)
                }
                return super.sendKeyEvent(event)
            }


            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength == 1 && afterLength == 0) {
                    backspaces.onNext(Unit)
                }
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
        }

        if (supportsInputContent) {
            EditorInfoCompat.setContentMimeTypes(editorInfo, arrayOf(
                    ContentType.IMAGE_JPEG,
                    ContentType.IMAGE_JPG,
                    ContentType.IMAGE_PNG,
                    ContentType.IMAGE_GIF))
        }

        val callback = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, opts ->
            val grantReadPermission = flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1 && grantReadPermission) {
                return@OnCommitContentListener tryOrNull {
                    inputContentInfo.requestPermission()
                    inputContentSelected.onNext(inputContentInfo)
                    true
                } ?: false

            }

            true
        }

        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback)
    }

}