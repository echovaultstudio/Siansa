package app.siansa.net

import app.siansa.data.AiProvider
import app.siansa.data.AppJson
import app.siansa.data.AppSettings
import app.siansa.data.Service
import app.siansa.servers.EveryServer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playlists' plans from Siansa Beats' AI (0.57, the owner, 18 Nov): asked of it when its capabilities say so, and
 * then the phone's own AI isn't used; without it (AudioMuse-AI and no Siansa Beats, or no AI on Siansa Beats) the
 * phone's AI plans them as before.
 */
class BeatsPlansTest {
    @After fun forget() = BeatsSound.restorePlans(false)

    private fun caps(plans: String) = BeatsApi.Capabilities.parse(AppJson.parseToJsonElement(
        """{"version":"0.57.0","api":1,"features":{$plans}}""",
    ) as JsonObject)

    private val phoneAi = AppSettings(aiProvider = AiProvider.OPENROUTER, aiKey = "sk", aiModel = "m")

    @Test fun capabilitiesSayWhetherItPlans() {
        assertTrue(caps(""""plans":{"available":true}""").plans)
        assertFalse(caps(""""plans":{"available":false}""").plans)
        assertFalse(caps(""""mixes":{"available":true}""").plans)  // a Siansa Beats from before 0.57
    }

    @Test fun beatsPlansSwitchThePhonesAiOff() {
        val audioMuseOnly = phoneAi.copy(audioMuseUrl = "http://am.local:8000", audioMuseToken = "t")
        val withBeats = phoneAi.copy(beatsUrl = "http://beats.local:8090")
        // AudioMuse-AI and no Siansa Beats: the phone's AI plans.
        BeatsSound.restorePlans(true)
        assertFalse(audioMuseOnly.beatsPlansReady)
        assertTrue(audioMuseOnly.plansReady)
        // Siansa Beats with its AI: it plans, the phone's AI isn't used.
        assertTrue(withBeats.beatsPlansReady)
        assertTrue(withBeats.copy(aiKey = "").plansReady)
        // Siansa Beats switched off, or without an AI: the phone's again.
        assertFalse(withBeats.copy(servicesOff = setOf(Service.BEATS.id)).beatsPlansReady)
        BeatsSound.restorePlans(false)
        assertFalse(withBeats.beatsPlansReady)
        assertTrue(withBeats.plansReady)
        assertFalse(withBeats.copy(aiKey = "").plansReady)
    }

    @Test fun thePlanIsAskedWithTheBrief() {
        for (server in EveryServer.all) {
            val seen = mutableListOf<Request>()
            val http = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                seen += chain.request()
                val body = if (chain.request().url.encodedPath == BeatsApi.PLAN)
                    """{"schema_version":1,"title":"Coffee before the storm","query":"warm acoustic guitar","description":"Easy.","steer":[{"term":"piano","direction":"more"}]}"""
                else """{"version":"0.57.0","api":1,"features":{"plans":{"available":true}}}"""
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }).build()
            val settings = server.settings.copy(beatsUrl = "http://beats:8090", beatsKey = "k3y")
            val plan = runBlocking { BeatsApi(http) { settings }.plan("Time slot: Morning\n\"Guidance\": calm") }
            assertEquals(server.name, "Coffee before the storm", plan["title"]?.jsonPrimitive?.content)
            val asked = seen.last { it.url.encodedPath == BeatsApi.PLAN }
            assertEquals(server.name, "POST", asked.method)
            assertEquals(server.name, "Bearer k3y", asked.header("Authorization"))
            val sent = AppJson.parseToJsonElement(Buffer().also { asked.body!!.writeTo(it) }.readUtf8()).jsonObject
            assertEquals(server.name, "Time slot: Morning\n\"Guidance\": calm", sent["brief"]?.jsonPrimitive?.content)
        }
    }
}
