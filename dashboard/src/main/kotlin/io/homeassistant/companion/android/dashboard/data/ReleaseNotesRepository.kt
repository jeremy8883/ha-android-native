package io.homeassistant.companion.android.dashboard.data

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/** An update's release notes (`update/release_notes`), as its more-info dialog loads them. */
class ReleaseNotesRepository @Inject constructor(private val sessions: ServerSessions) {
    /** [entityId]'s release notes in markdown, `null` when the update has none; a failure shows as such. */
    fun releaseNotes(entityId: String): Flow<Loadable<String?>> = sessions.withServer { session ->
        flow {
            val result = session.request(RELEASE_NOTES, mapOf("entity_id" to entityId)).flatMap { notes ->
                when {
                    notes == null || notes is JsonNull -> Fetched.Success(null)
                    notes is JsonPrimitive && notes.isString -> Fetched.Success(notes.content)
                    else -> Fetched.Failure(LoadError.UnexpectedResponse(RELEASE_NOTES))
                }
            }
            when (result) {
                is Fetched.Success -> emit(Loadable.Ready(result.value))
                is Fetched.Failure -> {
                    Timber.w("Couldn't load the release notes of $entityId: ${result.error}")
                    emit(Loadable.Failed(result.error))
                }
            }
        }
    }
}

private const val RELEASE_NOTES = "update/release_notes"
