package aman.catalogtest.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// === Duration formatting ================

fun Long.toHumanDuration(): String {
    val totalSeconds = this / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// === Timestamp formatting ==============================

private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

/** Formats a millisecond timestamp as a readable date string. Returns "—" for 0. */
fun Long.toDateString(): String =
    if (this > 0) timestampFormat.format(Date(this)) else "—"

/** Formats a second-based timestamp (e.g. Track.dateAdded) as a readable date string. */
fun Long.toDateAddedString(): String =
    if (this > 0) timestampFormat.format(Date(this * 1000L)) else "—"

// === Relative time formatting ===================

/** Formats a millisecond timestamp as a human-readable relative time.
 *  e.g. "Just now", "2 hours ago", "Yesterday", "3 days ago". */
fun Long.toRelativeTimeString(): String {
    if (this <= 0) return "Never"
    val diff    = System.currentTimeMillis() - this
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours   = minutes / 60
    val days    = hours / 24
    val weeks   = days / 7
    val months  = days / 30
    val years   = days / 365
    return when {
        seconds < 60  -> "Just now"
        minutes < 60  -> "$minutes min ago"
        hours == 1L   -> "1 hour ago"
        hours < 24    -> "$hours hours ago"
        days == 1L    -> "Yesterday"
        days < 7      -> "$days days ago"
        weeks == 1L   -> "1 week ago"
        weeks < 5     -> "$weeks weeks ago"
        months == 1L  -> "1 month ago"
        months < 12   -> "$months months ago"
        years == 1L   -> "1 year ago"
        else          -> "$years years ago"
    }
}

/** Formats a play count as a readable string. Returns null if never played. */
fun Int.toPlayCountString(): String? =
    if (this <= 0) null else if (this == 1) "1 play" else "$this plays"
