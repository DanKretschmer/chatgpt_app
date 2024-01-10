package com.kaishamarketing.chatgpt_app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import okhttp3.Interceptor
import okhttp3.Response as OkHttpResponse
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// Retrofit service interface
interface ChatGptApiService {
    @POST("v1/chat/completions")
    fun sendMessage(@Body message: RequestBody): Call<ChatGptResponse>
}


// Main activity
class MainActivity : AppCompatActivity() {
    private lateinit var apiService: ChatGptApiService
    private lateinit var editTextUserInput: EditText
    private lateinit var textViewChat: TextView
    private lateinit var buttonSend: Button

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save your layout specs

        outState.putString("EditTextContent", editTextUserInput.text.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        if (savedInstanceState != null) {
            val editTextContent = savedInstanceState.getString("EditTextContent")
            editTextUserInput.setText(editTextContent)

            // Apply these specs to your layout
        }
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        editTextUserInput = findViewById(R.id.editText)
        textViewChat = findViewById(R.id.textView)
        buttonSend = findViewById(R.id.button)
        initializeApiKey()
        // OkHttpClient with AuthInterceptor
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(this))
            .build()

        // Retrofit setup
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openai.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(ChatGptApiService::class.java)

        // Button click listener
        buttonSend.setOnClickListener {
            Log.d("MyAppError", "Button clicked")
            val userInput = editTextUserInput.text.toString()

            Log.d("MyAppError", "User input: $userInput")

            val json = """
    {
        "model": "gpt-3.5-turbo",
        "messages": [{"role": "user", "content": "$userInput"}],
        "temperature": 0.7
    }
""".trimIndent()


            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toRequestBody(mediaType)

            // API call
            val call = apiService.sendMessage(requestBody)
            call.enqueue(object : Callback<ChatGptResponse> {
                override fun onResponse(call: Call<ChatGptResponse>, response: Response<ChatGptResponse>) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody != null && responseBody.choices.isNotEmpty()) {
                            val assistantResponse = responseBody.choices.first { it.message.role == "assistant" }.message.content
                            textViewChat.text = assistantResponse
                        } else {
                            Log.d("MyAppError", "Response body is null")
                            Toast.makeText(this@MainActivity, "Received empty response", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Log.e("MyAppError", "Response error code: ${response.code()}")
                        Toast.makeText(this@MainActivity, "Response error", Toast.LENGTH_LONG).show()
                    }
                }


                override fun onFailure(call: Call<ChatGptResponse>, t: Throwable) {
                    Log.e("MyAppError", "Call failed with error: ${t.message}")
                    Toast.makeText(this@MainActivity, "Failed to send message: ${t.message}", Toast.LENGTH_LONG).show()
                }
            })

            editTextUserInput.text.clear()
        }
    }

    private fun initializeApiKey() {
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // Check if API key is already stored
        if (sharedPreferences.getString("apiKey", null) == null) {
            sharedPreferences.edit().apply {
                putString("apiKey", "") //your actual API key
                apply()
            }
        }
    }
}

// AuthInterceptor class
class AuthInterceptor(context: Context) : Interceptor {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "secret_shared_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun intercept(chain: Interceptor.Chain): OkHttpResponse  {
        val apiKey = sharedPreferences.getString("apiKey", null) ?: ""
        val newRequest = chain.request().newBuilder()
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
        return chain.proceed(newRequest)
    }
}

// Data class for the response
//data class ChatGptResponse(val message: String)

data class ChatGptResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<Choice>
)

data class Choice(
    val message: Message
)

data class Message(
    val role: String,
    val content: String
)

