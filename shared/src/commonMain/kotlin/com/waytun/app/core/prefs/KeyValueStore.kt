package com.waytun.app.core.prefs

/**
 * A small persistent key-value store for settings. Android backs it with the same SharedPreferences files the app
 * always used, so nothing is migrated and nothing is lost on update.
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun getInt(key: String, default: Int): Int
    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String)
        fun putInt(key: String, value: Int)
        fun remove(key: String)
    }
}
