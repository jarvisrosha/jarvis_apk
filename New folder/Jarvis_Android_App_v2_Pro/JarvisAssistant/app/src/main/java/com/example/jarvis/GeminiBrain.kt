package com.example.jarvis

import com.google.ai.client.generativeai.GenerativeModel
import org.json.JSONObject

data class JarvisDecision(
    val action: String,               // SCROLL_DOWN, SCROLL_UP, CLICK, OPEN_APP, VOLUME_UP, VOLUME_DOWN, BACK, HOME, MAKE_CALL, SEND_MESSAGE, SEND_MONEY, EDIT_TEXT, CHAT
    val target: String,               // व्यक्तीचे नाव, फोन नंबर, अ‍ॅपचे नाव, किंवा बटनाचे नाव
    val extraData: String,            // मेसेजचा मजकूर किंवा पैशांची रक्कम (UPI Amount)
    val languageCode: String,         // "mr-IN", "hi-IN", किंवा "en-IN" (युजरच्या भाषेनुसार)
    val requiresConfirmation: Boolean,// कॉल, मेसेज, पैसे पाठवणे, एडिट यासाठी नेहमी true
    val spokenResponse: String        // जार्विसने युजरच्याच भाषेत द्यायचे उत्तर किंवा परवानगीचा प्रश्न
)

class GeminiBrain(apiKey: String) {

    private val model = GenerativeModel(
        modelName = "gemini-2.5-flash",
        apiKey = apiKey
    )

    suspend fun analyzeCommand(userCommand: String): JarvisDecision {
        val systemPrompt = """
            तू 'Jarvis' नावाचा एक अत्यंत हुशार, अदृश्य (Invisible) मल्टी-लँग्वेज मोबाईल व्हॉईस असिस्टंट आहेस.
            युजर मराठी, हिंदी किंवा इंग्रजी अशा कोणत्याही भाषेत बोलेल. युजर ज्या भाषेत बोलला आहे, त्याच भाषेत 'spokenResponse' दे आणि 'languageCode' ("mr-IN", "hi-IN", किंवा "en-IN") सेट कर.
            
            तुला खालील शुद्ध JSON फॉरमॅटमध्येच उत्तर द्यायचे आहे:
            {
              "action": "SCROLL_DOWN | SCROLL_UP | CLICK | OPEN_APP | VOLUME_UP | VOLUME_DOWN | BACK | HOME | MAKE_CALL | SEND_MESSAGE | SEND_MONEY | EDIT_TEXT | CHAT",
              "target": "व्यक्तीचे नाव / फोन नंबर / बटनाचे नाव / अ‍ॅपचे नाव",
              "extraData": "पाठ्याचा मजकूर किंवा पैशांची रक्कम (असल्यास)",
              "languageCode": "mr-IN | hi-IN | en-IN",
              "requiresConfirmation": true किंवा false,
              "spokenResponse": "युजरला द्यायचे बोलके उत्तर किंवा परवानगी मागणारा प्रश्न"
            }

            कडेकोट सुरक्षा नियम (Safety Rules):
            1. परवानगीने होणारी कामे (requiresConfirmation = true):
               - MAKE_CALL (कोणालाही फोन लावणे): नेहमी requiresConfirmation = true ठेव! आणि spokenResponse मध्ये विचार: "सर, मी [नाव/नंबर] ला कॉल लावू का? हो किंवा नाही सांगा."
               - SEND_MESSAGE (WhatsApp किंवा SMS मेसेज पाठवणे): नेहमी requiresConfirmation = true ठेव! आणि विचार: "सर, मी [target] ला हा मेसेज पाठवू का?"
               - SEND_MONEY (पैसे पाठवणे / पेमेंट करणे): नेहमी requiresConfirmation = true ठेव! आणि विचार: "सर, मी [target] ला [extraData] रुपये पाठवण्यासाठी पेमेंट सुरू करू का?"
               - EDIT_TEXT (कोणताही मजकूर एडिट करणे किंवा डिलीट करणे): नेहमी requiresConfirmation = true ठेव!

            2. परवानगीशिवाय थेट होणारी कामे (requiresConfirmation = false):
               - SCROLL_DOWN, SCROLL_UP, CLICK, OPEN_APP, VOLUME_UP, VOLUME_DOWN, BACK, HOME, आणि सामान्य माहिती विचारल्यास (CHAT) थेट उत्तर दे, परवानगी मागू नकोस (requiresConfirmation = false).

            युजरची कमांड: "${userCommand}"
        """.trimIndent()

        return try {
            val response = model.generateContent(systemPrompt)
            val rawText = response.text?.trim()
                ?.removePrefix("```json")
                ?.removePrefix("```")
                ?.removeSuffix("```")
                ?.trim() ?: "{}"
            val json = JSONObject(rawText)
            val action = json.optString("action", "CHAT")
            // सुरक्षा खात्री: कॉल, मेसेज, पैसे आणि एडिटसाठी नेहमीच कन्फर्मेशन सक्तीचे करा
            val forcedConfirmation = action in listOf("MAKE_CALL", "SEND_MESSAGE", "SEND_MONEY", "EDIT_TEXT")
            JarvisDecision(
                action = action,
                target = json.optString("target", ""),
                extraData = json.optString("extraData", ""),
                languageCode = json.optString("languageCode", "mr-IN"),
                requiresConfirmation = forcedConfirmation || json.optBoolean("requiresConfirmation", false),
                spokenResponse = json.optString("spokenResponse", "जी सर.")
            )
        } catch (e: Exception) {
            JarvisDecision("CHAT", "", "", "mr-IN", false, "माफ करा सर, पुन्हा सांगा.")
        }
    }
}
