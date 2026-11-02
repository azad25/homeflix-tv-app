package com.homeflix.tv.livetv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.liveTvStore: DataStore<Preferences> by preferencesDataStore(name = "livetv")

/** What the TV remembers about Live TV; an interface so tests can use memory. */
interface LiveMemory {
    val favorites: Flow<Set<String>>
    suspend fun favoritesNow(): Set<String>
    suspend fun toggleFavorite(id: String)
    /** The channel being watched, and the one before it (for Back). */
    suspend fun lastAndPrevious(): Pair<String?, String?>
    /** Most recent first. */
    suspend fun recents(): List<String>
    suspend fun recordTune(id: String)
}

/** Favorites, last/previous channel and recently watched channels, in DataStore. */
@Singleton
class LiveTvStore @Inject constructor(@ApplicationContext private val context: Context) : LiveMemory {
    private val store get() = context.liveTvStore

    override val favorites: Flow<Set<String>> = store.data.map { it[FAVORITES].orEmpty() }

    override suspend fun favoritesNow(): Set<String> = favorites.first()

    override suspend fun toggleFavorite(id: String) {
        store.edit { p ->
            val now = p[FAVORITES].orEmpty()
            p[FAVORITES] = if (id in now) now - id else now + id
        }
    }

    override suspend fun lastAndPrevious(): Pair<String?, String?> {
        val p = store.data.first()
        return p[LAST] to p[PREVIOUS]
    }

    override suspend fun recents(): List<String> =
        store.data.first()[RECENTS]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

    override suspend fun recordTune(id: String) {
        store.edit { p ->
            val last = p[LAST]
            if (last != id) {
                if (last != null) p[PREVIOUS] = last
                p[LAST] = id
            }
            val recents = listOf(id) + (p[RECENTS]?.split('\n').orEmpty().filter { it.isNotBlank() && it != id })
            p[RECENTS] = recents.take(MAX_RECENTS).joinToString("\n")
        }
    }

    private companion object {
        val FAVORITES = stringSetPreferencesKey("favorites")
        val LAST = stringPreferencesKey("last")
        val PREVIOUS = stringPreferencesKey("previous")
        val RECENTS = stringPreferencesKey("recents")
        const val MAX_RECENTS = 10
    }
}
