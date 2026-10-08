package io.homeassistant.companion.android.dashboard.data.cache

import android.content.Context
import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.Transaction
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** A piece of server data as last loaded, by server: the raw server response(s) it is derived from. */
@Entity(tableName = "cached_value", primaryKeys = ["server_id", "name"])
data class CachedValue(
    @ColumnInfo(name = "server_id") val serverId: Int,
    val name: String,
    val json: String,
    /** When it was saved, in epoch milliseconds. */
    @ColumnInfo(name = "saved_at") val savedAt: Long,
)

/** One entity's state as last received, by server, in the compressed `subscribe_entities` form. */
@Entity(tableName = "cached_entity_state", primaryKeys = ["server_id", "entity_id"])
data class CachedEntityState(
    @ColumnInfo(name = "server_id") val serverId: Int,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val json: String,
)

@Dao
interface DashboardCacheDao {
    @Query("SELECT * FROM cached_value WHERE server_id = :serverId AND name = :name")
    suspend fun value(serverId: Int, name: String): CachedValue?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putValue(value: CachedValue)

    @Query("SELECT * FROM cached_entity_state WHERE server_id = :serverId")
    suspend fun states(serverId: Int): List<CachedEntityState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putStates(states: List<CachedEntityState>)

    @Query("DELETE FROM cached_entity_state WHERE server_id = :serverId AND entity_id IN (:entityIds)")
    suspend fun deleteStates(serverId: Int, entityIds: List<String>)

    @Query("DELETE FROM cached_entity_state WHERE server_id = :serverId")
    suspend fun deleteAllStates(serverId: Int)

    /** Save the states that changed and remove the removed ones, with when they were saved, all at once. */
    @Transaction
    suspend fun updateStates(
        serverId: Int,
        changed: List<CachedEntityState>,
        removed: List<String>,
        saved: CachedValue,
    ) {
        if (changed.isNotEmpty()) putStates(changed)
        removed.chunked(MAX_SQL_ARGUMENTS).forEach { deleteStates(serverId, it) }
        putValue(saved)
    }

    /** Replace all of the states of [serverId], with when they were saved, all at once. */
    @Transaction
    suspend fun replaceStates(serverId: Int, states: List<CachedEntityState>, saved: CachedValue) {
        deleteAllStates(serverId)
        states.chunked(MAX_SQL_ARGUMENTS).forEach { putStates(it) }
        putValue(saved)
    }

    @Query("SELECT DISTINCT server_id FROM cached_value")
    suspend fun serverIds(): List<Int>

    @Query("DELETE FROM cached_value WHERE server_id = :serverId")
    suspend fun deleteValues(serverId: Int)

    /** Forget everything cached for [serverId]. */
    @Transaction
    suspend fun deleteServer(serverId: Int) {
        deleteValues(serverId)
        deleteAllStates(serverId)
    }
}

/**
 * The native dashboards' cache: the last server data, to show dashboards before (or without) a connection. Separate
 * from the app's database so it never affects its migrations; it only holds copies, so a new version starts empty.
 */
@Database(entities = [CachedValue::class, CachedEntityState::class], version = 1, exportSchema = false)
abstract class DashboardCacheDatabase : RoomDatabase() {
    abstract fun dao(): DashboardCacheDao
}

@Module
@InstallIn(SingletonComponent::class)
internal object DashboardCacheModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DashboardCacheDatabase = Room
        .databaseBuilder(context, DashboardCacheDatabase::class.java, DATABASE_NAME)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

    @Provides
    fun provideDao(database: DashboardCacheDatabase): DashboardCacheDao = database.dao()
}

private const val DATABASE_NAME = "native_dashboard_cache"

/** SQLite's lowest limit on the arguments of one statement is 999. */
private const val MAX_SQL_ARGUMENTS = 500
