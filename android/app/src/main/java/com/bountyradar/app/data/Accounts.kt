package com.bountyradar.app.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.Immutable
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

// --------------------------------------------------------------------------- //
// Secure storage for platform API tokens.                                      //
// Tokens are encrypted with a key held in the Android Keystore and never leave //
// the device except in requests made directly to the platform that issued them.//
// --------------------------------------------------------------------------- //
class AccountsStore(context: Context) {

    private val prefs: SharedPreferences = open(context.applicationContext)

    private fun open(context: Context): SharedPreferences {
        fun create() = EncryptedSharedPreferences.create(
            FILE,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
        return try {
            create()
        } catch (e: Exception) {
            // Keystore key lost (e.g. app data restored on another device): start clean.
            context.deleteSharedPreferences(FILE)
            create()
        }
    }

    var h1Username: String
        get() = prefs.getString("h1_user", "").orEmpty()
        private set(v) = prefs.edit().putString("h1_user", v).apply()
    var h1Token: String
        get() = prefs.getString("h1_token", "").orEmpty()
        private set(v) = prefs.edit().putString("h1_token", v).apply()
    var intigritiToken: String
        get() = prefs.getString("inti_token", "").orEmpty()
        private set(v) = prefs.edit().putString("inti_token", v).apply()

    val hasHackerOne: Boolean get() = h1Username.isNotBlank() && h1Token.isNotBlank()
    val hasIntigriti: Boolean get() = intigritiToken.isNotBlank()

    fun saveHackerOne(username: String, token: String) {
        h1Username = username.trim(); h1Token = token.trim()
    }
    fun clearHackerOne() { prefs.edit().remove("h1_user").remove("h1_token").apply() }
    fun saveIntigriti(token: String) { intigritiToken = token.trim() }
    fun clearIntigriti() { prefs.edit().remove("inti_token").apply() }

    private companion object { const val FILE = "bountyradar_accounts" }
}

// --------------------------------------------------------------------------- //
// Models                                                                        //
// --------------------------------------------------------------------------- //
@Immutable data class AccountProgram(
    val name: String, val handle: String, val url: String,
    val isPrivate: Boolean, val bounty: Boolean, val detail: String = "",
)
@Immutable data class AccountReport(
    val title: String, val state: String, val program: String, val date: String, val url: String,
)
@Immutable data class AccountEarning(val amount: String, val program: String, val date: String)

@Immutable data class HackerOneData(
    val balance: String? = null,
    val programs: List<AccountProgram> = emptyList(),
    val reports: List<AccountReport> = emptyList(),
    val earnings: List<AccountEarning> = emptyList(),
    val warnings: List<String> = emptyList(),
)
@Immutable data class IntigritiData(
    val programs: List<AccountProgram> = emptyList(),
    val warnings: List<String> = emptyList(),
)

class ApiException(val code: Int, message: String) : IOException(message)

// --------------------------------------------------------------------------- //
// Direct calls to each platform's own researcher API.                           //
// --------------------------------------------------------------------------- //
object PlatformApi {

    private fun get(url: String, headers: Map<String, String>): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "BountyRadar-Android")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val hint = when (code) {
                    401, 403 -> "credentials rejected"
                    404 -> "endpoint not found"
                    429 -> "rate limited — try again in a minute"
                    else -> "request failed"
                }
                throw ApiException(code, "HTTP $code: $hint")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONObject.obj(vararg path: String): JSONObject? {
        var cur: JSONObject? = this
        for (p in path) cur = cur?.optJSONObject(p)
        return cur
    }

    // ---- HackerOne (official Hacker API, HTTP Basic: API username + token) ----
    private const val H1 = "https://api.hackerone.com/v1/hackers"

    suspend fun hackerOne(username: String, token: String): HackerOneData = withContext(Dispatchers.IO) {
        val basic = Base64.encodeToString("$username:$token".toByteArray(), Base64.NO_WRAP)
        val auth = mapOf("Authorization" to "Basic $basic")
        val warnings = mutableListOf<String>()

        // Programs first: if this fails with 401/403 the credentials are wrong — stop.
        val programs = mutableListOf<AccountProgram>()
        var next: String? = "$H1/programs?page%5Bsize%5D=100"
        var pages = 0
        while (next != null && pages < 8) {
            val body = JSONObject(get(next, auth))
            val data = body.optJSONArray("data")
            for (i in 0 until (data?.length() ?: 0)) {
                val a = data!!.getJSONObject(i).optJSONObject("attributes") ?: continue
                val handle = a.optString("handle")
                if (handle.isBlank()) continue
                val state = a.optString("state")
                programs += AccountProgram(
                    name = a.optString("name", handle),
                    handle = handle,
                    url = "https://hackerone.com/$handle",
                    isPrivate = state == "soft_launched",
                    bounty = a.optBoolean("offers_bounties"),
                    detail = a.optString("submission_state"),
                )
            }
            next = body.optJSONObject("links")?.optString("next")?.takeIf { it.isNotBlank() && it != "null" }
            pages++
        }

        val balance = runCatching {
            val data = JSONObject(get("$H1/payments/balance", auth)).optJSONObject("data")
            val value = data?.opt("balance") ?: data?.optJSONObject("attributes")?.opt("balance")
            value?.toString()
        }.onFailure { warnings += "Balance: ${it.message}" }.getOrNull()

        val reports = runCatching {
            val data = JSONObject(get("$H1/me/reports?page%5Bsize%5D=50", auth)).optJSONArray("data")
            List(data?.length() ?: 0) { i ->
                val o = data!!.getJSONObject(i)
                val a = o.optJSONObject("attributes") ?: JSONObject()
                AccountReport(
                    title = a.optString("title", "(untitled)"),
                    state = a.optString("state"),
                    program = o.obj("relationships", "program", "data", "attributes")
                        ?.optString("handle").orEmpty(),
                    date = a.optString("created_at").take(10),
                    url = "https://hackerone.com/reports/${o.optString("id")}",
                )
            }
        }.onFailure { warnings += "Reports: ${it.message}" }.getOrDefault(emptyList())

        val earnings = runCatching {
            val data = JSONObject(get("$H1/payments/earnings?page%5Bsize%5D=50", auth)).optJSONArray("data")
            List(data?.length() ?: 0) { i ->
                val o = data!!.getJSONObject(i)
                val a = o.optJSONObject("attributes") ?: JSONObject()
                AccountEarning(
                    amount = a.opt("amount")?.toString().orEmpty(),
                    program = o.obj("relationships", "program", "data", "attributes")
                        ?.optString("name").orEmpty(),
                    date = a.optString("created_at").take(10),
                )
            }
        }.onFailure { warnings += "Earnings: ${it.message}" }.getOrDefault(emptyList())

        HackerOneData(balance, programs, reports, earnings, warnings)
    }

    // ---- Intigriti (researcher API, personal access token as Bearer) ----
    suspend fun intigriti(token: String): IntigritiData = withContext(Dispatchers.IO) {
        val auth = mapOf("Authorization" to "Bearer $token")
        val body = JSONObject(
            get("https://api.intigriti.com/external/researcher/v1/programs?limit=500&offset=0", auth)
        )
        val records = body.optJSONArray("records")
        val programs = List(records?.length() ?: 0) { i ->
            val r = records!!.getJSONObject(i)
            val level = r.optJSONObject("confidentialityLevel")
            val max = r.optJSONObject("maxBounty")
            AccountProgram(
                name = r.optString("name", r.optString("handle")),
                handle = r.optString("handle"),
                url = r.optJSONObject("webLinks")?.optString("detail").orEmpty(),
                isPrivate = level != null && level.optInt("id", 4) != 4,
                bounty = (max?.optDouble("value", 0.0) ?: 0.0) > 0.0,
                detail = level?.optString("value").orEmpty(),
            )
        }
        IntigritiData(programs)
    }
}
