package uk.co.zenithal.carrel.data

import io.ktor.http.HttpMethod
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** The signed-in reader's profile (GET /profile). */
@Serializable
data class Profile(
    /** Null until the reader chooses one. */
    val username: String?,
    /** Whether anyone can see the reader's profile and library; if not, only they can. */
    val isPublic: Boolean,
    /** Null without a picture. */
    val avatarUrl: String?,
    /** Every year's goal the reader has set, oldest first. */
    val goals: List<ReadingGoal>,
)

@Serializable
data class ReadingGoal(val year: Int, val books: Int)

/** Any reader's public profile, with their whole library (GET /readers/{username}); a private one is not found. */
@Serializable
data class Reader(
    val username: String,
    val isPublic: Boolean,
    val avatarUrl: String?,
    val goals: List<ReadingGoal>,
    val library: List<LibraryItem>,
)

@Serializable
data class UsernameAvailability(val available: Boolean, val reason: String?)

@Serializable
data class SaveProfileRequest(val username: String, val isPublic: Boolean? = null)

@Serializable
data class SaveGoalRequest(val books: Int)

/** The reader's password, which the API checks before deleting their library or account. */
@Serializable
data class PasswordConfirmation(val password: String)

const val PROFILE_PATH = "/profile"

/** 3 to 20 letters, numbers, underscores, or hyphens, as the API checks. */
val VALID_USERNAME = Regex("^[A-Za-z0-9_-]{3,20}$")

private val GOALS = ListSerializer(ReadingGoal.serializer())

/** Sets or removes a year's reading goal, then puts the reader's goals into the saved profile, so they show at once. */
class GoalChanges(private val api: ApiClient, private val store: Store) {

    suspend fun save(year: Int, books: Int) =
        saved(api.send(HttpMethod.Put, "/profile/goals/$year", SaveGoalRequest(books), SaveGoalRequest.serializer(), GOALS))

    suspend fun remove(year: Int) = saved(api.delete("/profile/goals/$year", GOALS))

    private suspend fun saved(goals: List<ReadingGoal>) =
        store.update(PROFILE_PATH, Profile.serializer()) { it.copy(goals = goals) }
}
