package com.iven.musicplayergo.preferences

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.CompoundButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iven.musicplayergo.GoPreferences
import com.iven.musicplayergo.R
import com.iven.musicplayergo.utils.Theming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class FiltersAdapterTest {
    @Test
    fun recycledRowRestoresItsOwnSelectionAndTitleColor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val result = AtomicReference<Result<Unit>>()
        instrumentation.runOnMainSync {
            result.set(runCatching { verifyRecycledRow(instrumentation.targetContext) })
        }
        result.get().getOrThrow()
    }

    private fun verifyRecycledRow(targetContext: Context) {
        val preferences = GoPreferences.getPrefsInstance()
        val originalFilters = preferences.filters
        try {
            preferences.filters = setOf("A", "B")
            val context = ContextThemeWrapper(
                targetContext,
                R.style.BaseTheme_Default
            )
            val parent = RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
            }
            val adapter = FiltersAdapter()
            val holder = adapter.onCreateViewHolder(parent, 0)
            val checkbox = holder.itemView.findViewById<CompoundButton>(R.id.filter)
            val title = holder.itemView.findViewById<TextView>(R.id.title)

            adapter.onBindViewHolder(holder, 0)
            assertEquals("A", title.text.toString())
            assertTrue(checkbox.isChecked)
            assertTrue(holder.itemView.performClick())
            assertFalse(checkbox.isChecked)

            adapter.onBindViewHolder(holder, 1)
            assertEquals("B", title.text.toString())
            assertTrue("B stays selected after the holder is reused", checkbox.isChecked)
            assertEquals(
                Theming.resolveColorAttr(context, android.R.attr.textColorPrimary),
                title.currentTextColor
            )

            adapter.onBindViewHolder(holder, 0)
            assertEquals("A", title.text.toString())
            assertFalse("A stays deselected after rebinding", checkbox.isChecked)
            assertEquals(Theming.resolveWidgetsColorNormal(context), title.currentTextColor)
            assertEquals(setOf("B"), adapter.getUpdatedItems())
        } finally {
            preferences.filters = originalFilters
        }
    }
}
