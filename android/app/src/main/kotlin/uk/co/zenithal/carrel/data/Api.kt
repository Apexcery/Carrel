package uk.co.zenithal.carrel.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/** A failed API call, with a message to show the reader. Status 0 means the API couldn't be reached. */
class ApiException(val status: Int, message: String) : Exception(message)

/** Calls the Carrel API as the signed-in reader, turning failures into the same messages as the website. */
class ApiClient(
    private val baseUrl: String,
    private val supabase: SupabaseClient,
    val json: Json,
) {
    private val http = HttpClient(OkHttp) { expectSuccess = false }

    /** The response body of a GET, as text (the store keeps it as it came). */
    suspend fun getText(path: String): String = call(HttpMethod.Get, path, null).bodyAsText()

    suspend fun <T> get(path: String, serializer: KSerializer<T>): T = json.decodeFromString(serializer, getText(path))

    /** Like get, but null when the API answers 404. */
    suspend fun <T> getOrNull(path: String, serializer: KSerializer<T>): T? =
        try {
            get(path, serializer)
        } catch (e: ApiException) {
            if (e.status == 404) null else throw e
        }

    /** Sends a JSON body and returns the parsed response. */
    suspend fun <B, T> send(
        method: HttpMethod,
        path: String,
        body: B,
        bodySerializer: KSerializer<B>,
        resultSerializer: KSerializer<T>,
    ): T = json.decodeFromString(resultSerializer, call(method, path, json.encodeToString(bodySerializer, body)).bodyAsText())

    /** Sends a JSON body to an endpoint that answers with no content. */
    suspend fun <B> send(method: HttpMethod, path: String, body: B, bodySerializer: KSerializer<B>) {
        call(method, path, json.encodeToString(bodySerializer, body))
    }

    /** POSTs without a body, e.g. to resume an import, and returns the parsed response. */
    suspend fun <T> post(path: String, resultSerializer: KSerializer<T>): T =
        json.decodeFromString(resultSerializer, call(HttpMethod.Post, path, null).bodyAsText())

    /** POSTs without a body to an endpoint that answers with no content. */
    suspend fun post(path: String) {
        call(HttpMethod.Post, path, null)
    }

    /** POSTs a file as the request body (an export to import, a profile picture) and returns the parsed response. */
    suspend fun <T> upload(path: String, file: ByteArray, type: ContentType, resultSerializer: KSerializer<T>): T =
        json.decodeFromString(resultSerializer, call(HttpMethod.Post, path, file, type).bodyAsText())

    /** A GET's response as bytes, e.g. a library export to save. */
    suspend fun download(path: String): ByteArray = call(HttpMethod.Get, path, null).bodyAsBytes()

    suspend fun delete(path: String) {
        call(HttpMethod.Delete, path, null)
    }

    /** A DELETE that returns what's left, e.g. the remaining reading goals. */
    suspend fun <T> delete(path: String, resultSerializer: KSerializer<T>): T =
        json.decodeFromString(resultSerializer, call(HttpMethod.Delete, path, null).bodyAsText())

    private suspend fun call(method: HttpMethod, path: String, body: String?): HttpResponse =
        call(method, path, body, ContentType.Application.Json)

    // The body is sent whole (not streamed), since the API turns away uploads without a Content-Length.
    private suspend fun call(method: HttpMethod, path: String, body: Any?, type: ContentType): HttpResponse {
        val response = try {
            http.request("$baseUrl$path") {
                this.method = method
                supabase.auth.currentAccessTokenOrNull()?.let { bearerAuth(it) }
                if (body != null) {
                    contentType(type)
                    setBody(body)
                }
            }
        } catch (e: IOException) {
            throw ApiException(0, CANT_CONNECT)
        }
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, messageFor(response))
        }
        return response
    }

    private suspend fun messageFor(response: HttpResponse): String = when (response.status.value) {
        // Validation problems carry a readable message per field.
        400 -> runCatching {
            json.parseToJsonElement(response.bodyAsText()).jsonObject["errors"]?.jsonObject?.values
                ?.firstOrNull()?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        }.getOrNull() ?: "That wasn’t accepted. Check what you entered."
        401 -> "Your session has expired. Sign in again."
        404 -> "We couldn’t find that."
        // Waits longer than a minute only come from the daily allowance for signed-out browsing.
        429 -> if ((response.headers["Retry-After"]?.toIntOrNull() ?: 0) > 60) {
            "Carrel is busy, so browsing without an account is paused for now. Sign in to carry on, or try again later."
        } else {
            "That’s a lot of requests in a short time. Wait a minute and try again."
        }
        503 -> "Book data is temporarily unavailable. Try again in a few minutes."
        else -> "Something went wrong. Try again."
    }

    companion object {
        const val CANT_CONNECT = "Can’t reach Carrel right now. Check your connection and try again."
    }
}

/** The JSON settings shared by the API client and the store: the API's camelCase names, ignoring fields not used. */
val CarrelJson = Json { ignoreUnknownKeys = true }
