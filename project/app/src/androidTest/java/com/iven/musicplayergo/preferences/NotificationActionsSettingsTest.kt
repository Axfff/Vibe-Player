package com.iven.musicplayergo.preferences

import android.view.KeyEvent
import android.view.View
import android.widget.RadioButton
import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.dialogs.RecyclerSheet
import com.iven.musicplayergo.models.NotificationAction
import com.iven.musicplayergo.regression.RegressionFixture
import com.iven.musicplayergo.utils.Theming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationActionsSettingsTest {
    @Test
    fun cancelAndBackDiscardPendingChoiceWhileOkSavesIt() {
        RegressionFixture.open().use { fixture ->
            val original = NotificationAction(GoConstants.REPEAT_ACTION, GoConstants.CLOSE_ACTION)
            val replacement = NotificationAction(GoConstants.REWIND_ACTION, GoConstants.FAST_FORWARD_ACTION)
            fixture.onActivity { activity ->
                fixture.preferences.notificationActions = original
                val settingsIndex = fixture.preferences.activeTabs.indexOf(GoConstants.SETTINGS_TAB)
                activity.findViewById<ViewPager2>(R.id.view_pager2).setCurrentItem(settingsIndex, false)
            }
            fixture.await("Settings is resumed") {
                fixture.onMain {
                    settings(fixture)?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true
                }
            }

            openSheet(fixture)
            selectReplacement(fixture)
            pressButton(fixture, R.id.btnNegative)
            assertSaved(fixture, original, "Cancel")

            openSheet(fixture)
            assertSelectedRow(fixture, 0)
            selectReplacement(fixture)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitClosedSheet(fixture)
            assertSaved(fixture, original, "Back")

            openSheet(fixture)
            assertSelectedRow(fixture, 0)
            selectReplacement(fixture)
            assertSaved(fixture, original, "Pending selection")
            pressButton(fixture, R.id.btnPositive)
            assertSaved(fixture, replacement, "OK")

            openSheet(fixture)
            assertSelectedRow(fixture, 1)
            pressButton(fixture, R.id.btnNegative)
        }
    }

    private fun openSheet(fixture: RegressionFixture) {
        val position = fixture.onMain {
            val fragment = settings(fixture)!!
            val preference = notificationPreference(fixture)
            (fragment.listView.adapter as PreferenceGroupAdapter)
                .getPreferenceAdapterPosition(preference).also {
                    assertTrue(it >= 0)
                    fragment.listView.scrollToPosition(it)
                }
        }
        fixture.await("Additional notification actions preference is laid out") {
            fixture.onMain { settings(fixture)!!.listView.findViewHolderForAdapterPosition(position) != null }
        }
        fixture.onMain {
            assertTrue(settings(fixture)!!.listView.findViewHolderForAdapterPosition(position)!!.itemView.performClick())
        }
        fixture.await("Notification actions sheet has laid out its options") {
            fixture.onMain { option(fixture, 0) != null && option(fixture, 1) != null }
        }
    }

    private fun selectReplacement(fixture: RegressionFixture) {
        fixture.onMain { assertTrue(option(fixture, 1)!!.performClick()) }
        assertSelectedRow(fixture, 1)
    }

    private fun assertSelectedRow(fixture: RegressionFixture, position: Int) {
        fixture.await("Only notification action row $position is selected among the first two choices") {
            fixture.onMain {
                (0..1).all { index ->
                    option(fixture, index)?.findViewById<RadioButton>(R.id.radio)?.isChecked == (index == position)
                }
            }
        }
    }

    private fun pressButton(fixture: RegressionFixture, id: Int) {
        fixture.onMain { assertTrue(sheet(fixture)!!.requireView().findViewById<View>(id).performClick()) }
        awaitClosedSheet(fixture)
    }

    private fun awaitClosedSheet(fixture: RegressionFixture) {
        fixture.await("Notification actions sheet is closed") { fixture.onMain { sheet(fixture) == null } }
    }

    private fun assertSaved(fixture: RegressionFixture, expected: NotificationAction, action: String) {
        fixture.onMain {
            assertEquals("$action should leave the expected saved actions", expected, fixture.preferences.notificationActions)
            assertEquals(
                "$action should leave the expected Settings summary",
                fixture.context.getString(Theming.getNotificationActionTitle(expected.first)),
                notificationPreference(fixture).summary.toString()
            )
        }
    }

    private fun option(fixture: RegressionFixture, position: Int): View? =
        sheet(fixture)?.view?.findViewById<RecyclerView>(R.id.modal_rv)
            ?.findViewHolderForAdapterPosition(position)?.itemView

    private fun notificationPreference(fixture: RegressionFixture): Preference =
        settings(fixture)!!.findPreference<Preference>(fixture.context.getString(R.string.notif_actions_pref))!!

    private fun settings(fixture: RegressionFixture): PreferencesFragment? {
        var preferences: PreferencesFragment? = null
        fixture.onActivity { activity ->
            preferences = activity.supportFragmentManager.fragments.filterIsInstance<SettingsFragment>()
                .singleOrNull()?.getPreferencesFragment()
        }
        return preferences
    }

    private fun sheet(fixture: RegressionFixture): RecyclerSheet? {
        var sheet: RecyclerSheet? = null
        fixture.onActivity { activity ->
            sheet = activity.supportFragmentManager.findFragmentByTag(RecyclerSheet.TAG_MODAL_RV) as? RecyclerSheet
        }
        return sheet
    }
}
