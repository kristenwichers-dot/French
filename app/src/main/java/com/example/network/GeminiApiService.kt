package com.example.network

import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GeminiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class MissingApiKeyException(
        message: String = "Gemini API key is not configured. Please add GEMINI_API_KEY to your Secrets panel, .env file, or enter it in App Settings."
    ) : GeminiException(message)

    class AuthenticationException(
        message: String = "Gemini API authentication failed (HTTP 401/403). Please verify your API key."
    ) : GeminiException(message)

    class RateLimitException(
        message: String = "Gemini API rate limit exceeded (HTTP 429). Please wait a moment and try again."
    ) : GeminiException(message)

    class NetworkException(
        message: String = "Network connection failed while reaching Gemini. Please check your internet connection.",
        cause: Throwable? = null
    ) : GeminiException(message, cause)

    class ServerException(
        val code: Int,
        message: String = "Gemini service temporarily unavailable (HTTP $code)."
    ) : GeminiException(message)

    class ContentBlockedException(
        message: String = "The response was blocked or filtered by Gemini content safety policies."
    ) : GeminiException(message)

    class EmptyResponseException(
        message: String = "Gemini returned an empty response. Please rephrase or try again."
    ) : GeminiException(message)
}

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Header("x-goog-api-key") apiKey: String,
        @Path("model") model: String = "gemini-3.5-flash",
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    const val DEFAULT_MODEL = "gemini-3.5-flash"

    // Optional user runtime key override stored in app preferences
    @Volatile
    private var customApiKey: String? = null

    fun setCustomApiKey(key: String?) {
        customApiKey = key?.trim()
    }

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Redacted logger that NEVER logs secret headers, base64 images, or audio payloads
    private val logging = HttpLoggingInterceptor { message ->
        // Sanitize any accidental token/key references
        if (!message.contains("x-goog-api-key", ignoreCase = true) && !message.contains("inline_data", ignoreCase = true)) {
            android.util.Log.d("GeminiClient", message)
        }
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(logging)
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }

    fun getApiKey(): String {
        val custom = customApiKey
        if (!custom.isNullOrBlank() && custom != "MY_GEMINI_API_KEY") {
            return custom
        }
        val buildKey = BuildConfig.GEMINI_API_KEY
        if (buildKey.isNotBlank() && buildKey != "MY_GEMINI_API_KEY") {
            return buildKey
        }
        return ""
    }

    fun isKeyConfigured(): Boolean {
        val key = getApiKey()
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    suspend fun executeGenerateContent(
        request: GenerateContentRequest,
        model: String = DEFAULT_MODEL
    ): String {
        val key = getApiKey()
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            throw GeminiException.MissingApiKeyException()
        }

        try {
            val response = service.generateContent(
                apiKey = key,
                model = model,
                request = request
            )

            val candidate = response.candidates?.firstOrNull()
            if (candidate?.finishReason == "SAFETY") {
                throw GeminiException.ContentBlockedException()
            }

            val text = candidate?.content?.parts?.firstOrNull()?.text
            if (text.isNullOrBlank()) {
                if (response.promptFeedback?.blockReason != null) {
                    throw GeminiException.ContentBlockedException("Prompt blocked: ${response.promptFeedback.blockReason}")
                }
                throw GeminiException.EmptyResponseException()
            }

            return text
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> throw GeminiException.ServerException(400, "Bad request to Gemini model $model. Check parameters.")
                401, 403 -> throw GeminiException.AuthenticationException()
                429 -> throw GeminiException.RateLimitException()
                in 500..599 -> throw GeminiException.ServerException(e.code())
                else -> throw GeminiException.ServerException(e.code(), e.message())
            }
        } catch (e: IOException) {
            throw GeminiException.NetworkException("Network error connecting to Gemini: ${e.localizedMessage}", e)
        } catch (e: GeminiException) {
            throw e
        } catch (e: Exception) {
            throw GeminiException.NetworkException("Unexpected error during Gemini call: ${e.localizedMessage}", e)
        }
    }
}
