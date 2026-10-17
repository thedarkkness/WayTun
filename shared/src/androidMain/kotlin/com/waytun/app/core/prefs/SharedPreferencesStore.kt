package com.waytun.app.core.prefs

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesStore(private val prefs: SharedPreferences) : KeyValueStore {

    constructor(context: Context, name: String) : this(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    override fun edit(block: KeyValueStore.Editor.() -> Unit) {
        val editor = prefs.edit()
        object : KeyValueStore.Editor {
            override fun putString(key: String, value: String) { editor.putString(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }.block()
        editor.apply()
    }
}
