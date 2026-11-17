package com.focusflow.app.domain

/** Plain snapshot of one accessibility node, so classification can be unit tested off-device. */
data class UiNode(
    val viewId: String? = null,
    val text: String? = null,
    val contentDescription: String? = null,
    val className: String? = null,
    /** Selected or checked: bottom-bar tabs report their current tab this way. */
    val selected: Boolean = false,
    val children: List<UiNode> = emptyList(),
) {
    /** The id without its package prefix: "com.google.android.youtube:id/reel_recycler" -> "reel_recycler". */
    val shortId: String? get() = viewId?.substringAfter(":id/", viewId)

    fun label(): String? = (contentDescription?.takeIf { it.isNotBlank() } ?: text?.takeIf { it.isNotBlank() })

    fun walk(): Sequence<UiNode> = sequence {
        yield(this@UiNode)
        for (c in children) yieldAll(c.walk())
    }
}

enum class YouTubeScreen { SHORTS_PLAYER, HOME_FEED, OTHER }

data class YouTubeClassification(
    val screen: YouTubeScreen,
    /** Human-readable reasons, shown in the detector test screen. */
    val signals: List<String>,
)

/**
 * Decides which YouTube screen is showing, from the accessibility tree only.
 *
 * IMPORTANT: YouTube has no public API for this and changes its interface regularly. The view-id
 * names below were observed in recent app versions but are NOT guaranteed for the version
 * installed on a given phone. To stay resilient the classifier uses two independent signals
 * (view ids, and the selected bottom-bar tab), and the in-app detector test shows which signal
 * fired so a broken id can be spotted and fixed. Tab labels are matched in English only; the
 * id signal is language independent.
 */
object YouTubeUiClassifier {
    const val PACKAGE = "com.google.android.youtube"

    /** Id prefixes that only exist inside the full-screen Shorts player (not the Home-feed Shorts shelf). */
    private val shortsPlayerIdPrefixes = listOf(
        "reel_recycler",
        "reel_player_page_container",
        "reel_player_overlay",
        "reel_watch_fragment_root",
        "reel_time_bar",
        "reel_progress_bar",
        "shorts_player",
    )

    private val shortsTabLabels = setOf("shorts")
    private val homeTabLabels = setOf("home")
    private val subscriptionsTabLabels = setOf("subscriptions")

    fun classify(root: UiNode): YouTubeClassification {
        val signals = mutableListOf<String>()
        var shorts = false

        for (n in root.walk()) {
            val id = n.shortId
            if (id != null && shortsPlayerIdPrefixes.any { id.startsWith(it) }) {
                signals += "Shorts player view id: $id"
                shorts = true
            }
            val label = n.label()?.trim()?.lowercase()
            if (n.selected && label != null && label in shortsTabLabels) {
                signals += "Shorts tab is selected"
                shorts = true
            }
        }
        if (shorts) return YouTubeClassification(YouTubeScreen.SHORTS_PLAYER, signals.distinct())

        val homeSelected = root.walk().any {
            it.selected && (it.label()?.trim()?.lowercase() in homeTabLabels)
        }
        if (homeSelected) {
            return YouTubeClassification(YouTubeScreen.HOME_FEED, listOf("Home tab is selected"))
        }
        return YouTubeClassification(YouTubeScreen.OTHER, emptyList())
    }

    /** Labels used to find the tab to switch to. */
    fun isHomeTabLabel(label: String?) = label?.trim()?.lowercase() in homeTabLabels
    fun isSubscriptionsTabLabel(label: String?) = label?.trim()?.lowercase() in subscriptionsTabLabels

    /** A sample of ids seen in the tree, so users can report what a changed YouTube version looks like. */
    fun sampleIds(root: UiNode, limit: Int = 40): List<String> =
        root.walk().mapNotNull { it.shortId }.distinct().take(limit).toList()
}
