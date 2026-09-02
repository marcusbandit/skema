package com.marcusrosado.skemafordtu.data

/**
 * The app owns exactly three small files: the settings, the cached feed, and
 * a note of when that feed arrived. Common code cannot touch a filesystem, so
 * each platform supplies its own directory and this is the whole interface
 * between them.
 */
interface Vault {
    fun read(name: String): String?
    fun write(name: String, content: String)
    fun delete(name: String)

    /** Where the files actually live, for the settings screen to show. */
    val location: String
}

expect fun vault(): Vault

const val FEED_FILE = "feed.ics"
const val META_FILE = "feed.meta.json"
const val SETTINGS_FILE = "settings.json"
const val SESSION_FILE = "session.json"
