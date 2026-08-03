package com.rootapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide holder for the currently selected sky theme, so a change on the League screen
 * updates the app-wide background live. Backed by [SeasonStore] for persistence.
 */
object SkyThemeState {
    private val _selected = MutableStateFlow(SkyTheme.DEFAULT)
    val selected: StateFlow<SkyTheme> = _selected.asStateFlow()

    fun init(context: Context) { _selected.value = SeasonStore(context).selectedTheme() }

    fun set(context: Context, theme: SkyTheme) {
        SeasonStore(context).setSelected(theme.key)
        _selected.value = theme
    }
}
