package com.focusflow.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUiClassifierTest {
    private fun id(name: String) = "com.google.android.youtube:id/$name"

    private fun bottomBar(selected: String): UiNode = UiNode(
        viewId = id("pivot_bar"),
        children = listOf("Home", "Shorts", "Subscriptions", "You").map {
            UiNode(contentDescription = it, selected = it == selected)
        },
    )

    @Test
    fun `full screen shorts player is detected by its view id`() {
        val tree = UiNode(
            children = listOf(
                UiNode(viewId = id("reel_recycler")),
                UiNode(contentDescription = "Like this video"),
            ),
        )
        val c = YouTubeUiClassifier.classify(tree)
        assertEquals(YouTubeScreen.SHORTS_PLAYER, c.screen)
        assertTrue(c.signals.any { "reel_recycler" in it })
    }

    @Test
    fun `selected shorts tab is detected even if ids changed`() {
        val tree = UiNode(children = listOf(UiNode(viewId = id("something_new")), bottomBar("Shorts")))
        assertEquals(YouTubeScreen.SHORTS_PLAYER, YouTubeUiClassifier.classify(tree).screen)
    }

    @Test
    fun `shorts shelf on the home feed is not blocked`() {
        // The Home feed contains a "Shorts" header and shelf, but the Shorts tab is not selected
        // and there is no full-screen player id.
        val tree = UiNode(
            children = listOf(
                UiNode(text = "Shorts"),
                UiNode(viewId = id("reel_shelf_item")),
                bottomBar("Home"),
            ),
        )
        assertEquals(YouTubeScreen.HOME_FEED, YouTubeUiClassifier.classify(tree).screen)
    }

    @Test
    fun `normal video watch page is other`() {
        val tree = UiNode(
            children = listOf(
                UiNode(viewId = id("watch_player")),
                UiNode(contentDescription = "Like this video"),
                UiNode(contentDescription = "Comments"),
            ),
        )
        assertEquals(YouTubeScreen.OTHER, YouTubeUiClassifier.classify(tree).screen)
    }

    @Test
    fun `search and subscriptions are other`() {
        assertEquals(YouTubeScreen.OTHER, YouTubeUiClassifier.classify(bottomBar("Subscriptions")).screen)
        assertEquals(
            YouTubeScreen.OTHER,
            YouTubeUiClassifier.classify(UiNode(viewId = id("search_edit_text"))).screen,
        )
    }

    @Test
    fun `home tab is matched case-insensitively`() {
        val tree = UiNode(children = listOf(UiNode(contentDescription = " HOME ", selected = true)))
        assertEquals(YouTubeScreen.HOME_FEED, YouTubeUiClassifier.classify(tree).screen)
    }

    @Test
    fun `an unselected shorts label alone does not trigger`() {
        val tree = UiNode(children = listOf(UiNode(contentDescription = "Shorts", selected = false)))
        assertEquals(YouTubeScreen.OTHER, YouTubeUiClassifier.classify(tree).screen)
    }

    @Test
    fun `sample ids are shortened and distinct`() {
        val tree = UiNode(children = listOf(UiNode(viewId = id("a")), UiNode(viewId = id("a")), UiNode(viewId = id("b"))))
        assertEquals(listOf("a", "b"), YouTubeUiClassifier.sampleIds(tree))
    }
}
