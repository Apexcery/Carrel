package uk.co.zenithal.carrel.data

import kotlinx.serialization.Serializable

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

@Serializable
data class UsernameAvailability(val available: Boolean, val reason: String?)

@Serializable
data class SaveProfileRequest(val username: String, val isPublic: Boolean? = null)

const val PROFILE_PATH = "/profile"

/** 3 to 20 letters, numbers, underscores, or hyphens, as the API checks. */
val VALID_USERNAME = Regex("^[A-Za-z0-9_-]{3,20}$")
