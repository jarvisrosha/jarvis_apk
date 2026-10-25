package com.example.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var tts: TextToSpeech
    private lateinit var wakeLock: PowerManager.WakeLock
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    // मल्टी-लँग्वेज वेकवर्ड्स (मराठी, हिंदी, इंग्लिश)
    private val wakeWords = listOf(
        "hey jarvis", "jarvis", "ok jarvis", "okay jarvis", "hello jarvis",
        "हे जार्विस", "जार्विस", "ओके जार्विस", "हॅलो जार्विस", "अरे जार्विस"
    )

    // परवानगी मागितलेला निर्णय साठवण्यासाठी (कॉल, मेसेज, पेमेंट, एडिट)
    private var pendingDecision: JarvisDecision? = null
    private var isAwakeForCommand = false

    override fun onCreate() {
        super.onCreate()
        // १. स्क्रीन बंद (Lock) असतानाही मोबाईल दूर ठेवला तरी CPU आणि माईक चालू ठेवण्यासाठी PARTIAL_WAKE_LOCK
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "JarvisAI::AlwaysOnWakeLock"
        )
        wakeLock.acquire()

        // २. किमान शक्य तितके शांत (Silent/Minimal) बॅकग्राऊंड नोटिफिकेशन
        startForeground(1, createSilentNotification())
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()
        startListening()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("mr", "IN")
        }
    }

    // युजरच्या भाषेनुसार (मराठी / हिंदी / इंग्लिश) आवाज बदलून बोलणे
    private fun speak(text: String, langCode: String = "mr-IN") {
        val locale = when (langCode) {
            "hi-IN" -> Locale("hi", "IN")
            "en-IN", "en-US" -> Locale("en", "IN")
            else -> Locale("mr", "IN")
        }
        tts.language = locale
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_VOICE")
    }

    private fun initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()?.lowercase(Locale.getDefault()) ?: ""
                if (spokenText.isNotEmpty()) {
                    handleSpokenInput(spokenText)
                }
                startListening()
            }

            override fun onError(error: Int) {
                startListening()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // भारतातील मल्टी-लँग्वेज (मराठी + हिंदी + इंग्लिश) ओळखण्यासाठी
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "mr-IN")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
        }
        try {
            speechRecognizer.startListening(intent)
        } catch (e: Exception) {}
    }

    private fun handleSpokenInput(input: String) {
        // १. जर कॉल, मेसेज, पैसे पाठवणे किंवा एडिटसाठी परवानगी मागितली असेल तर 'हो/Yes' किंवा 'नाही/No' तपासा
        pendingDecision?.let { decision ->
            val yesWords = listOf("हो", "हा", "हां", "चालेल", "कर", "लाव", "पाठव", "yes", "yeah", "sure", "ok", "okay", "go ahead")
            val noWords = listOf("नाही", "नको", "थांब", "रद्द", "मत करो", "नहीं", "no", "cancel", "stop", "don't")

            if (yesWords.any { input.contains(it) }) {
                pendingDecision = null
                executeAction(decision)
                return
            } else if (noWords.any { input.contains(it) }) {
                pendingDecision = null
                speak("ठीक आहे सर, मी ते काम रद्द केले आहे.", decision.languageCode)
                return
            }
        }

        // २. वेकवर्ड तपासणे ('Hey Jarvis', 'Jarvis', 'OK Jarvis')
        val matchedWakeWord = wakeWords.firstOrNull { input.contains(it) }
        if (matchedWakeWord != null) {
            val commandAfterWakeWord = input.substringAfter(matchedWakeWord).trim()
            if (commandAfterWakeWord.isEmpty()) {
                isAwakeForCommand = true
                speak("बोला सर, मी ऐकत आहे.")
                return
            } else {
                processUserCommand(commandAfterWakeWord)
                return
            }
        }

        if (isAwakeForCommand) {
            isAwakeForCommand = false
            processUserCommand(input)
        }
    }

    private fun processUserCommand(command: String) {
        // परवानगीशिवाय चालणाऱ्या वेगवान कमांड्स (Fast Local Auto-Actions)
        when {
            command.contains("स्क्रोल डाऊन") || command.contains("scroll down") || command.contains("खाली घे") || command.contains("नीचे स्क्रोल") -> {
                JarvisAccessibilityService.instance?.scrollScreen(directionDown = true)
                speak("स्क्रोल डाऊन केले")
                return
            }
            command.contains("स्क्रोल अप") || command.contains("scroll up") || command.contains("वर घे") || command.contains("ऊपर स्क्रोल") -> {
                JarvisAccessibilityService.instance?.scrollScreen(directionDown = false)
                speak("स्क्रोल अप केले")
                return
            }
        }

        val apiKey = getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            speak("सर, कृपया आधी अ‍ॅपमध्ये Gemini API Key सेव्ह करा.")
            return
        }

        serviceScope.launch {
            val brain = GeminiBrain(apiKey)
            val decision = brain.analyzeCommand(command)

            // जर कॉल लावायचा असेल तर फोनच्या कॉन्टॅक्ट्समधून नाव शोधून नंबर काढा
            if (decision.action == "MAKE_CALL") {
                val contactMatch = ContactHelper.findPhoneNumberByName(this@JarvisVoiceService, decision.target)
                if (contactMatch != null) {
                    val (matchedName, matchedNumber) = contactMatch
                    val updatedDecision = decision.copy(
                        target = matchedNumber,
                        requiresConfirmation = true,
                        spokenResponse = "सर, मी ${matchedName} यांना कॉल लावू का? हो किंवा नाही सांगा."
                    )
                    pendingDecision = updatedDecision
                    speak(updatedDecision.spokenResponse, updatedDecision.languageCode)
                    return@launch
                }
            }

            speak(decision.spokenResponse, decision.languageCode)

            if (decision.requiresConfirmation) {
                // कॉल, मेसेज, पैसे पाठवणे किंवा एडिटसाठी तुमची परवानगी मिळेपर्यंत वाट पहा
                pendingDecision = decision
            } else {
                // इतर सर्व कामे थेट आवाजाने पूर्ण करा
                executeAction(decision)
            }
        }
    }

    private fun executeAction(decision: JarvisDecision) {
        val acc = JarvisAccessibilityService.instance
        when (decision.action) {
            "SCROLL_DOWN" -> acc?.scrollScreen(true)
            "SCROLL_UP" -> acc?.scrollScreen(false)
            "CLICK" -> acc?.clickByText(decision.target)
            "BACK" -> acc?.performBack()
            "HOME" -> acc?.performHome()
            "VOLUME_UP" -> adjustVolume(true)
            "VOLUME_DOWN" -> adjustVolume(false)
            "OPEN_APP" -> openAppByName(decision.target)
            "EDIT_TEXT" -> {
                acc?.editFocusedText(decision.extraData.ifEmpty { decision.target })
                speak("मजकूर एडिट केला आहे सर.", decision.languageCode)
            }
            "MAKE_CALL" -> {
                val number = decision.target.filter { it.isDigit() || it == '+' }
                if (number.isNotEmpty()) {
                    val callIntent = Intent(Intent.ACTION_CALL).apply {
                        data = Uri.parse("tel:$number")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(callIntent)
                    speak("कॉल लावत आहे सर.", decision.languageCode)
                }
            }
            "SEND_MESSAGE" -> {
                sendMessageToContact(decision.target, decision.extraData, decision.languageCode)
            }
            "SEND_MONEY" -> {
                openUpiPayment(decision.target, decision.extraData, decision.languageCode)
            }
        }
    }

    private fun adjustVolume(increase: Boolean) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val direction = if (increase) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        audioManager.adjustVolume(direction, AudioManager.FLAG_PLAY_SOUND)
    }

    private fun openAppByName(appName: String) {
        val pm = packageManager
        val packages = pm.getInstalledApplications(0)
        val matchedApp = packages.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(appName, ignoreCase = true)
        }
        matchedApp?.let {
            val launchIntent = pm.getLaunchIntentForPackage(it.packageName)
            launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
        }
    }

    private fun sendMessageToContact(targetNameOrNumber: String, messageText: String, lang: String) {
        val contact = ContactHelper.findPhoneNumberByName(this, targetNameOrNumber)
        val number = contact?.second ?: targetNameOrNumber.filter { it.isDigit() || it == '+' }
        if (number.isNotEmpty() && messageText.isNotEmpty()) {
            try {
                val smsManager = SmsManager.getDefault()
                smsManager.sendTextMessage(number, null, messageText, null, null)
                speak("मेसेज पाठवला आहे सर.", lang)
            } catch (e: Exception) {
                // WhatsApp द्वारे पाठवण्याचा पर्याय
                val waIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$number&text=${Uri.encode(messageText)}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(waIntent)
            }
        }
    }

    private fun openUpiPayment(receiverName: String, amount: String, lang: String) {
        speak("सर, पेमेंट अ‍ॅप उघडत आहे. सुरक्षिततेसाठी फक्त तुमचा UPI पिन टाका.", lang)
        val upiIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.nbu.paisa.user") // GPay
            ?: packageManager.getLaunchIntentForPackage("com.phonepe.app") // PhonePe
        upiIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (upiIntent != null) startActivity(upiIntent)
    }

    private fun createSilentNotification(): Notification {
        val channelId = "jarvis_invisible_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Jarvis Background Engine",
                NotificationManager.IMPORTANCE_MIN // स्क्रीनवर कोणताही पॉप-अप किंवा आवाज येणार नाही
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis Invisible Active")
            .setContentText("Always listening for 'Hey Jarvis'...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
