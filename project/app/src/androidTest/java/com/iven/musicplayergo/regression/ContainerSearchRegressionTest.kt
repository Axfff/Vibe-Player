package com.iven.musicplayergo.regression

import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.iven.musicplayergo.GoConstants
import com.iven.musicplayergo.R
import com.iven.musicplayergo.fragments.MusicContainersFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise empty results and recovery through each container tab's real SearchView. */
@RunWith(AndroidJUnit4::class)
class ContainerSearchRegressionTest {
    @Test fun unmatchedArtistSearchClearsRowsAndClearingQueryRestoresThem() =
        searchAndClear(GoConstants.ARTISTS_TAB, GoConstants.ARTIST_VIEW)

    @Test fun unmatchedAlbumSearchClearsRowsAndClearingQueryRestoresThem() =
        searchAndClear(GoConstants.ALBUM_TAB, GoConstants.ALBUM_VIEW)

    @Test fun unmatchedFolderSearchClearsRowsAndClearingQueryRestoresThem() =
        searchAndClear(GoConstants.FOLDERS_TAB, GoConstants.FOLDER_VIEW)

    private fun searchAndClear(tab: String, source: String) {
        RegressionFixture.open().use { f ->
            f.onActivity { activity ->
                val index = f.preferences.activeTabs.indexOf(tab)
                assertTrue("Container tab is enabled", index >= 0)
                activity.findViewById<ViewPager2>(R.id.view_pager2).setCurrentItem(index, false)
            }
            fun fragment(): MusicContainersFragment? {
                var result: MusicContainersFragment? = null
                f.onActivity { activity ->
                    result = activity.supportFragmentManager.fragments
                        .filterIsInstance<MusicContainersFragment>()
                        .firstOrNull { it.arguments?.getString("SELECTED_FRAGMENT") == source }
                }
                return result
            }
            f.await("$tab displays the scanned library") {
                f.onMain {
                    val rows = fragment()?.view?.findViewById<RecyclerView>(R.id.artists_folders_rv)
                    rows?.isShown == true && rows.adapter?.itemCount?.let { it > 0 } == true &&
                        rows.findViewHolderForAdapterPosition(0) != null
                }
            }
            f.onMain {
                val root = fragment()!!.requireView()
                val rows = root.findViewById<RecyclerView>(R.id.artists_folders_rv)
                val initialCount = rows.adapter!!.itemCount
                val firstTitle = rows.findViewHolderForAdapterPosition(0)!!.itemView
                    .findViewById<TextView>(R.id.title).text.toString()
                assertTrue("Fixture container has a searchable name", firstTitle.isNotEmpty())
                val searchItem = root.findViewById<Toolbar>(R.id.search_toolbar).menu
                    .findItem(R.id.action_search)
                searchItem.expandActionView()
                val search = searchItem.actionView as SearchView
                search.setQuery(firstTitle, false)
                val matchedCount = rows.adapter!!.itemCount
                assertTrue("Existing container still matches", matchedCount > 0)

                search.setQuery("no-container-has-this-regression-query", false)
                assertEquals("An unmatched $tab query must remove stale rows", 0, rows.adapter!!.itemCount)

                search.setQuery(firstTitle, false)
                assertEquals("A matching query recovers after empty results", matchedCount, rows.adapter!!.itemCount)
                search.setQuery("", false)
                assertEquals("Clearing search restores all $tab rows", initialCount, rows.adapter!!.itemCount)
                search.clearFocus()
            }
        }
    }
}
