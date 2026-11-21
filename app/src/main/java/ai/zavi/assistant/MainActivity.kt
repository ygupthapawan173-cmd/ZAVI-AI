package ai.zavi.assistant

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import ai.zavi.assistant.data.TeluguOcrEngine
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ai.zavi.assistant.core.AgentWorkflow
import ai.zavi.assistant.core.CalculatorEngine
import ai.zavi.assistant.core.DeviceAction
import ai.zavi.assistant.core.DeviceActionLauncher
import ai.zavi.assistant.core.DeviceActionPlanner
import ai.zavi.assistant.core.ReminderManager
import ai.zavi.assistant.core.ZaviReminder
import ai.zavi.assistant.data.ConversationStore
import ai.zavi.assistant.data.DocumentTextExtractor
import ai.zavi.assistant.data.ImportedDocument
import ai.zavi.assistant.data.ImageAttachmentReader
import ai.zavi.assistant.data.ImportedImage
import ai.zavi.assistant.models.ChatMessage
import ai.zavi.assistant.network.AiProvider
import ai.zavi.assistant.network.ProviderConfig
import ai.zavi.assistant.network.MultiProviderClient
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.Locale

class MainActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            this,
            "zavi_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val conversationStore by lazy { ConversationStore(securePrefs) }
    private val multiProviderClient by lazy { MultiProviderClient() }
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechResult: ((String) -> Unit)? = null
    private var speechError: ((String) -> Unit)? = null
    private var textToSpeech: TextToSpeech? = null
    private var textToSpeechReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textToSpeech = TextToSpeech(this) { status ->
            textToSpeechReady = status == TextToSpeech.SUCCESS
            if (textToSpeechReady) {
                textToSpeech?.language = Locale.forLanguageTag("te-IN")
            }
        }

        setContent {
            ZaviApp(
                loadProviders = { loadProviderConfigs() },
                saveProviders = { saveProviderConfigs(it) },
                testProvider = { multiProviderClient.test(it) },
                readDocument = { uri -> DocumentTextExtractor.read(this, uri) },
                readImage = { uri -> ImageAttachmentReader.read(this, uri) },
                createCameraUri = { createCameraImageUri() },
                recognizeImageText = { uri, success, failure -> recognizeImageText(uri, success, failure) },
                loadMemory = { conversationStore.loadMemory() },
                saveMemory = { conversationStore.saveMemory(it) },
                loadMessages = { conversationStore.loadMessages() },
                saveMessages = { conversationStore.saveMessages(it) },
                clearMessages = { conversationStore.clearMessages() },
                clearMemory = { conversationStore.clearMemory() },
                ask = { prompt, history, memory, _ ->
                    val (state, messages) = AgentWorkflow.prepare(prompt, history, memory)
                    val answer = multiProviderClient.complete(loadProviderConfigs(), messages)
                    AgentReply(answer, state.selectedAgent.title)
                },
                launchDeviceAction = { action -> DeviceActionLauncher.launch(this, action) },
                startVoice = { onResult, onError -> startVoiceRecognition(onResult, onError) },
                stopVoice = { stopVoiceRecognition() },
                speak = { speakText(it) },
                requestNotificationPermission = {
                    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7301)
                    }
                },
                loadReminders = { ReminderManager.list(this) },
                addReminder = { title, delay, daily -> ReminderManager.add(this, title, delay, daily) },
                setReminderEnabled = { id, enabled -> ReminderManager.setEnabled(this, id, enabled) },
                deleteReminder = { id -> ReminderManager.delete(this, id) },
                shareChat = { messages ->
                    val transcript = messages.joinToString("\n\n") { message ->
                        val speaker = if (message.isUser) "You" else "ZAVI AI"
                        "$speaker: ${message.content}"
                    }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "ZAVI AI chat")
                        putExtra(Intent.EXTRA_TEXT, transcript)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share ZAVI chat"))
                }
            )
        }
    }

    private fun stopVoiceRecognition() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        speechResult = null
        speechError = null
    }

    @Suppress("DEPRECATION")
    private fun startVoiceRecognition(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("ఈ ఫోన్‌లో speech recognition అందుబాటులో లేదు. Google appలో Voice input enable చేయండి.")
            return
        }

        // Recreate the recognizer for each tap. Reusing a recognizer after ERROR_CLIENT
        // can leave Android's speech service in a stale state and repeat error code 5.
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
            speechResult = onResult
            speechError = onError

            val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = recognizer
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_CLIENT ->
                            "Voice service reset అయింది. Mic మళ్లీ నొక్కండి; అవసరమైతే Google app update చేయండి."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            "Microphone permission ఇవ్వండి."
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            "Voice recognition కోసం internet connection చూడండి."
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "మాటలు స్పష్టంగా వినిపించలేదు. మళ్లీ ప్రయత్నించండి."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            "Voice service busyగా ఉంది. కొద్దిసేపటి తర్వాత ప్రయత్నించండి."
                        else -> "Voice recognition failed (code $error). మళ్లీ ప్రయత్నించండి."
                    }
                    speechError?.invoke(message)
                    speechResult = null
                    speechError = null
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }

                override fun onResults(results: Bundle?) {
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (spoken.isNotBlank()) {
                        speechResult?.invoke(spoken)
                    } else {
                        speechError?.invoke("Voice clearగా వినిపించలేదు. మళ్లీ చెప్పండి.")
                    }
                    speechResult = null
                    speechError = null
                    speechRecognizer?.destroy()
                    speechRecognizer = null
                }
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "te-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "te-IN")
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            recognizer.startListening(intent)
        } catch (error: Exception) {
            speechResult = null
            speechError = null
            speechRecognizer?.destroy()
            speechRecognizer = null
            onError(error.message ?: "Voice input ప్రారంభం కాలేదు.")
        }
    }

    private fun createCameraImageUri(): Uri {
        val directory = File(cacheDir, "camera").apply { mkdirs() }
        val imageFile = File.createTempFile("zavi_capture_", ".jpg", directory)
        return FileProvider.getUriForFile(this, packageName + ".fileprovider", imageFile)
    }

    private fun recognizeImageText(uri: Uri, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        lifecycleScope.launch {
            try {
                val text = TeluguOcrEngine.recognize(this@MainActivity, uri)
                onSuccess(text)
            } catch (error: Exception) {
                onFailure(error.localizedMessage ?: "Telugu + English OCR failed")
            }
        }
    }
    private fun loadProviderConfigs(): List<ProviderConfig> = AiProvider.values().map { provider ->
        val id = provider.name.lowercase()
        val legacyKey = if (provider == AiProvider.OPENROUTER) securePrefs.getString(KEY_OPENROUTER, "") else ""
        val legacyModel = if (provider == AiProvider.OPENROUTER) securePrefs.getString(KEY_MODEL, provider.defaultModel) else provider.defaultModel
        ProviderConfig(provider, securePrefs.getString("provider_${id}_key", legacyKey) ?: legacyKey.orEmpty(),
            securePrefs.getString("provider_${id}_model", legacyModel) ?: provider.defaultModel,
            securePrefs.getBoolean("provider_${id}_enabled", provider == AiProvider.OPENROUTER && !legacyKey.isNullOrBlank()))
    }

    private fun saveProviderConfigs(configs: List<ProviderConfig>) {
        val editor = securePrefs.edit()
        configs.forEach { config ->
            val id = config.provider.name.lowercase()
            editor.putString("provider_${id}_key", config.apiKey.trim())
            editor.putString("provider_${id}_model", config.model.trim().ifBlank { config.provider.defaultModel })
            editor.putBoolean("provider_${id}_enabled", config.enabled)
            if (config.provider == AiProvider.OPENROUTER) {
                editor.putString(KEY_OPENROUTER, config.apiKey.trim())
                editor.putString(KEY_MODEL, config.model.trim().ifBlank { DEFAULT_MODEL })
            }
        }
        editor.apply()
    }

    private fun speakText(text: String) {
        if (textToSpeechReady && text.isNotBlank()) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "zavi-answer")
        }
    }

    override fun onDestroy() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    companion object {
        private const val KEY_OPENROUTER = "openrouter_key"
        private const val KEY_MODEL = "openrouter_model"
        private const val DEFAULT_MODEL = "openrouter/free"
    }
}

private data class AgentReply(
    val text: String,
    val agent: String
)

@Composable
private fun ZaviApp(
    loadProviders: () -> List<ProviderConfig>,
    saveProviders: (List<ProviderConfig>) -> Unit,
    testProvider: suspend (ProviderConfig) -> String,
    readDocument: (Uri) -> ImportedDocument,
    readImage: (Uri) -> ImportedImage,
    createCameraUri: () -> Uri,
    recognizeImageText: (Uri, (String) -> Unit, (String) -> Unit) -> Unit,
    loadMemory: () -> String,
    saveMemory: (String) -> Unit,
    loadMessages: () -> List<ChatMessage>,
    saveMessages: (List<ChatMessage>) -> Unit,
    clearMessages: () -> Unit,
    clearMemory: () -> Unit,
    ask: suspend (String, List<ChatMessage>, String, String) -> AgentReply,
    launchDeviceAction: (DeviceAction) -> String,
    startVoice: ((String) -> Unit, (String) -> Unit) -> Unit,
    stopVoice: () -> Unit,
    speak: (String) -> Unit,
    shareChat: (List<ChatMessage>) -> Unit,
    requestNotificationPermission: () -> Unit,
    loadReminders: () -> List<ZaviReminder>,
    addReminder: (String, Long, Boolean) -> Unit,
    setReminderEnabled: (String, Boolean) -> Unit,
    deleteReminder: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("HOME") }
    var attachedDocument by remember { mutableStateOf<ImportedDocument?>(null) }
    var attachedImage by remember { mutableStateOf<ImportedImage?>(null) }
    var providers by remember { mutableStateOf(loadProviders()) }
    var providerTestStatus by remember { mutableStateOf("") }
    var memory by remember { mutableStateOf(loadMemory()) }
    var showSettings by remember { mutableStateOf(false) }
    var showAutomation by remember { mutableStateOf(false) }
    var reminders by remember { mutableStateOf(loadReminders()) }
    var reminderTitle by remember { mutableStateOf("") }
    var reminderDelay by remember { mutableStateOf("15") }
    var reminderDaily by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voiceReplies by remember { mutableStateOf(false) }
    var handsFreeMode by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<DeviceAction?>(null) }
    val lines = remember { mutableStateListOf<ChatMessage>().apply { addAll(loadMessages()) } }
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                attachedImage = readImage(uri)
                voiceStatus = "Image attached and ready for AI vision"
            } catch (error: Exception) {
                voiceStatus = error.message ?: "Image could not be read."
            }
        }
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val uri = cameraUri
        if (captured && uri != null) {
            try {
                attachedImage = readImage(uri)
                voiceStatus = "Photo attached • Reading visible text…"
                recognizeImageText(uri, { text ->
                    if (text.isNotBlank()) {
                        prompt = (prompt + "\n\nText found in photo:\n" + text).trim()
                        voiceStatus = "Photo attached • Text extracted. Telugu image understanding depends on the selected AI model."
                    } else {
                        voiceStatus = "Photo attached • No readable text found. Ask ZAVI to inspect the image."
                    }
                }, { error -> voiceStatus = "Photo attached • OCR unavailable: " + error + ". AI image analysis is still available." })
            } catch (error: Exception) {
                voiceStatus = error.message ?: "Captured photo could not be attached."
            }
        } else { voiceStatus = "Camera capture cancelled." }
        cameraUri = null
    }
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                attachedDocument = readDocument(uri)
                voiceStatus = "Document attached and ready"
            } catch (error: Exception) {
                voiceStatus = error.message ?: "Document could not be read."
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                handsFreeMode = false
                stopVoice()
                voiceStatus = "App backgroundలోకి వెళ్లింది. Hey ZAVI microphone ఆపివేశాను."
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            handsFreeMode = true
            voiceReplies = true
            voiceStatus = "Hey ZAVI కోసం వింటున్నాను…"
        } else {
            handsFreeMode = false
            voiceStatus = "Wake Word కోసం microphone permission అవసరం."
        }
    }

    LaunchedEffect(handsFreeMode) {
        if (handsFreeMode) {
            var waitingForCommand = false
            while (isActive && handsFreeMode) {
                voiceStatus = if (waitingForCommand) "చెప్పండి… మీ command వింటున్నాను" else "Hey ZAVI కోసం వింటున్నాను…"
                val heard = suspendCancellableCoroutine<String?> { continuation ->
                    startVoice(
                        { result -> if (continuation.isActive) continuation.resume(result) },
                        { error ->
                            if (continuation.isActive) continuation.resume(null)
                            voiceStatus = error
                        }
                    )
                    continuation.invokeOnCancellation { stopVoice() }
                }
                if (!isActive || !handsFreeMode) break
                if (heard.isNullOrBlank()) {
                    delay(700)
                    continue
                }

                val normalized = heard.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
                val wakePhrases = listOf("hey zavi", "hey savvy", "hey zावी", "హే జావి", "హే జావీ")
                if (!waitingForCommand) {
                    val wakePhrase = wakePhrases.firstOrNull { normalized.contains(it) }
                    if (wakePhrase != null) {
                        val commandInline = normalized.substringAfter(wakePhrase).trim()
                        if (commandInline.isNotBlank()) {
                            prompt = heard.substringAfter(wakePhrase, "").trim().ifBlank { heard.trim() }
                            waitingForCommand = false
                        } else {
                            waitingForCommand = true
                            voiceStatus = "చెప్పండి… ఇప్పుడు command చెప్పండి"
                            speak("చెప్పండి")
                            delay(1200)
                            continue
                        }
                    } else {
                        delay(250)
                        continue
                    }
                }

                val command = if (waitingForCommand) heard.trim() else prompt.trim()
                waitingForCommand = false
                prompt = ""
                if (command.isBlank()) {
                    delay(300)
                    continue
                }
                if (command.lowercase().contains("stop listening") || command.contains("ఆపు")) {
                    handsFreeMode = false
                    voiceStatus = "Hey ZAVI listening ఆపివేశాను."
                    break
                }

                lines.add(ChatMessage(role = "user", content = command))
                saveMessages(lines.toList())
                busy = true
                voiceStatus = "మీ command అందింది… ZAVI ఆలోచిస్తోంది"
                try {
                    val reply = ask(command, lines.dropLast(1), loadMemory(), "")
                    lines.add(ChatMessage(role = "assistant", content = reply.text, agent = reply.agent))
                    saveMessages(lines.toList())
                    voiceStatus = if (reply.text.isBlank()) "AI నుంచి ఖాళీ response వచ్చింది. Settingsలో API provider పరీక్షించండి." else "ZAVI సమాధానం ఇచ్చింది."
                    if (reply.text.isNotBlank()) speak(reply.text)
                } catch (error: Exception) {
                    val message = error.message ?: "AI request failed"
                    lines.add(ChatMessage(role = "assistant", content = message))
                    saveMessages(lines.toList())
                    voiceStatus = "AI error: $message"
                    speak("క్షమించండి. AI connection సమస్య ఉంది.")
                } finally {
                    busy = false
                }
                delay(900)
            }
            stopVoice()
        }
    }

    val bg = Brush.verticalGradient(
        listOf(Color(0xFF080B18), Color(0xFF17102B), Color(0xFF080B18))
    )

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B5CF6),
            secondary = Color(0xFF22D3EE),
            background = Color(0xFF080B18)
        )
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(bg)
                .padding(16.dp)
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF97316))),
                                    RoundedCornerShape(18.dp)
                                ).padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF080B18), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                Text("Z", fontSize = 31.sp, fontWeight = FontWeight.Black, color = Color.White)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("ZAVI AI", fontSize = 25.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                            Text("YOUR ALL-IN-ONE AI", fontSize = 9.sp, color = Color(0xFF67E8F9), fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                            Text("Telugu • English • Cloud intelligence", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            if (handsFreeMode) {
                                handsFreeMode = false
                                stopVoice()
                                voiceStatus = "Wake Word mode ఆపివేశాను."
                            } else {
                                micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }) {
                            Text(if (handsFreeMode) "🟢 Hey ZAVI ON" else "🎙 Hey ZAVI", color = if (handsFreeMode) Color(0xFF86EFAC) else Color(0xFF67E8F9), fontSize = 11.sp)
                        }
                        TextButton(onClick = { voiceReplies = !voiceReplies }) {
                            Text(if (voiceReplies) "🔊 Voice ON" else "🔇 Voice OFF", color = Color(0xFF67E8F9), fontSize = 11.sp)
                        }
                        TextButton(onClick = { shareChat(lines.toList()) }) {
                            Text("Share", color = Color(0xFFC084FC), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                ) {
                    Box(
                        Modifier.fillMaxWidth().background(
                            Brush.linearGradient(listOf(Color(0xFF172554), Color(0xFF31205C), Color(0xFF24113D)))
                        ).padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("SMART BRAIN 2.0", color = Color(0xFF67E8F9), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                                Text("● ACTIVE", color = Color(0xFF86EFAC), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Your AI workspace", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("Auto Model • Multi-provider fallback • Telugu + English", color = Color(0xFFD1D5DB), fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf(
                                    "${providers.count { it.enabled && it.apiKey.isNotBlank() }} Providers",
                                    "${reminders.size} Reminders",
                                    if (voiceReplies) "Voice ON" else "Text mode"
                                ).forEach { item ->
                                    Box(Modifier.weight(1f).background(Color(0x3322D3EE), RoundedCornerShape(12.dp)).padding(vertical = 9.dp, horizontal = 5.dp), contentAlignment = Alignment.Center) {
                                        Text(item, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("QUICK ACTIONS", color = Color(0xFF9CA3AF), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { prompt = "Search the web for: " }, modifier = Modifier.weight(1f)) {
                        Text("🌐 Web", color = Color(0xFF67E8F9), fontSize = 11.sp)
                    }
                    TextButton(onClick = { prompt = "Help me write or debug code for: " }, modifier = Modifier.weight(1f)) {
                        Text("💻 Code", color = Color(0xFFC084FC), fontSize = 11.sp)
                    }
                    TextButton(onClick = { prompt = "Create social media content about: " }, modifier = Modifier.weight(1f)) {
                        Text("✨ Create", color = Color(0xFFF0ABFC), fontSize = 11.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { prompt = "Research this topic in depth: " }, modifier = Modifier.weight(1f)) {
                        Text("🔎 Research", color = Color(0xFF67E8F9), fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        reminders = loadReminders()
                        requestNotificationPermission()
                        showAutomation = true
                    }, modifier = Modifier.weight(1f)) {
                        Text("⏰ Automation", color = Color(0xFFC084FC), fontSize = 11.sp)
                    }
                    TextButton(onClick = {
                        providers = loadProviders()
                        providerTestStatus = ""
                        memory = loadMemory()
                        showSettings = true
                    }, modifier = Modifier.weight(1f)) {
                        Text("⚙ Settings", color = Color(0xFF67E8F9), fontSize = 11.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        try { val uri = createCameraUri(); cameraUri = uri; cameraLauncher.launch(uri) }
                        catch (error: Exception) { voiceStatus = error.localizedMessage ?: "Camera could not start." }
                    }, modifier = Modifier.weight(1f)) { Text("📷 Camera + OCR", color = Color(0xFFF0ABFC), fontSize = 11.sp) }
                    TextButton(onClick = { prompt = "Search the web for YouTube videos about: " }, modifier = Modifier.weight(1f)) { Text("▶ YouTube", color = Color(0xFF67E8F9), fontSize = 11.sp) }
                    TextButton(onClick = { prompt = "Create a Telugu + English YouTube Shorts script, caption and hashtags about: " }, modifier = Modifier.weight(1f)) { Text("🎬 Creator", color = Color(0xFFC084FC), fontSize = 11.sp) }
                }
                if (voiceStatus.isNotBlank()) {
                    Text(voiceStatus, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (lines.isEmpty()) {
                        item {
                            Text(
                                "నమస్కారం! నేను ZAVI AI. ఏ విషయం గురించి సహాయం కావాలి?",
                                color = Color.White,
                                modifier = Modifier
                                    .background(Color(0xFF20243A), RoundedCornerShape(18.dp))
                                    .padding(14.dp)
                            )
                        }
                    }
                    items(lines) { line ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (line.isUser) Alignment.End else Alignment.Start
                        ) {
                            if (!line.isUser && line.agent.isNotBlank()) {
                                Text(line.agent, color = Color(0xFF67E8F9), fontSize = 10.sp)
                            }
                            val safeContent = line.content.takeUnless {
                                it.isBlank() || it.equals("null", ignoreCase = true)
                            } ?: "AI నుంచి ఖాళీ సమాధానం వచ్చింది. మళ్లీ ప్రయత్నించండి."
                            Text(
                                safeContent,
                                modifier = Modifier.widthIn(max = 320.dp)
                                    .background(
                                        if (line.isUser) Color(0xFF49318A) else Color(0xFF20243A),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .padding(14.dp),
                                color = Color.White
                            )
                        }
                    }
                    if (busy) {
                        item { Text("ZAVI is thinking…", color = Color(0xFF67E8F9)) }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF20243A), RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = { imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp")) },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Image, contentDescription = "Attach image", tint = Color(0xFF67E8F9))
                    }
                    IconButton(
                        onClick = { documentPicker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown", "text/csv", "application/json", "application/xml", "text/html")) },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach document", tint = Color(0xFFC084FC))
                    }
                    TextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message ZAVI…", color = Color.Gray) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF67E8F9),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )
                    IconButton(
                        onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice input", tint = Color(0xFFC084FC))
                    }
                    IconButton(
                        onClick = {
                            val query = prompt.trim()
                            val document = attachedDocument
                            val image = attachedImage
                            if ((query.isNotEmpty() || document != null || image != null) && !busy) {
                                val action = DeviceActionPlanner.plan(query)
                                val calculation = if (action == null) CalculatorEngine.tryCalculate(query) else null
                                when {
                                    action != null -> {
                                        pendingAction = action
                                        prompt = ""
                                        voiceStatus = "Android action కోసం confirmation కావాలి."
                                    }
                                    calculation != null -> {
                                        lines.add(ChatMessage(role = "user", content = query))
                                        val result = "ఫలితం: $calculation"
                                        lines.add(ChatMessage(role = "assistant", content = result, agent = "Calculator"))
                                        saveMessages(lines.toList())
                                        prompt = ""
                                        voiceStatus = "Phoneలోనే calculate చేశాను — API call లేదు."
                                        if (voiceReplies) speak(result)
                                    }
                                    else -> {
                                        val previousHistory = lines.toList()
                                        val visiblePrompt = (query.ifBlank {
                                            when {
                                                image != null -> "Analyze this image"
                                                document != null -> "Analyze this document"
                                                else -> "Help me"
                                            }
                                        }) + (if (document != null) "\n📎 " + document.name else "") +
                                            (if (image != null) "\n🖼️ " + image.name else "")
                                        val imageBlock = image?.let { "\n\n" + it.toPromptBlock() }.orEmpty()
                                        val documentBlock = if (document != null) {
                                            "\n\nDOCUMENT CONTENT (" + document.name + "):\n" + document.text
                                        } else ""
                                        val defaultRequest = when {
                                            image != null -> "Describe this image in detail. Read visible text if any and answer in Telugu."
                                            document != null -> "Summarize this document, explain key points, and answer in Telugu."
                                            else -> ""
                                        }
                                        val aiPrompt = query + documentBlock + imageBlock +
                                            (if (defaultRequest.isNotBlank()) "\n\nUser request: " + query.ifBlank { defaultRequest } else "")
                                        lines.add(ChatMessage(role = "user", content = visiblePrompt))
                                        saveMessages(lines.toList())
                                        prompt = ""
                                        attachedDocument = null
                                        attachedImage = null
                                        voiceStatus = ""
                                        busy = true
                                        scope.launch {
                                            try {
                                                val reply = ask(aiPrompt, previousHistory, loadMemory(), "")
                                                lines.add(
                                                    ChatMessage(
                                                        role = "assistant",
                                                        content = reply.text,
                                                        agent = reply.agent
                                                    )
                                                )
                                                saveMessages(lines.toList())
                                                if (voiceReplies) speak(reply.text)
                                            } catch (error: Exception) {
                                                lines.add(
                                                    ChatMessage(
                                                        role = "assistant",
                                                        content = error.message ?: "Request failed"
                                                    )
                                                )
                                                saveMessages(lines.toList())
                                            } finally {
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !busy
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = Color(0xFF67E8F9))
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(Color(0xFF171A2D), RoundedCornerShape(22.dp))
                        .padding(horizontal = 5.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("HOME", "CHAT", "TOOLS", "SETTINGS").forEach { tab ->
                        val active = selectedTab == tab
                        TextButton(
                            onClick = {
                                selectedTab = tab
                                if (tab == "SETTINGS") {
                                    providers = loadProviders()
                                    providerTestStatus = ""
                                    memory = loadMemory()
                                    showSettings = true
                                }
                                if (tab == "CHAT") voiceStatus = "Chat ready — message ZAVI AI below."
                                if (tab == "TOOLS") voiceStatus = "Choose a tool from Quick Actions."
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when (tab) { "HOME" -> "⌂ Home"; "CHAT" -> "▣ Chat"; "TOOLS" -> "✦ Tools"; else -> "⚙ Settings" },
                                color = if (active) Color(0xFF67E8F9) else Color(0xFF9CA3AF),
                                fontSize = 10.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            pendingAction?.let { action ->
                AlertDialog(
                    onDismissRequest = { pendingAction = null },
                    title = { Text(action.title) },
                    text = { Text(action.description) },
                    confirmButton = {
                        TextButton(onClick = {
                            voiceStatus = launchDeviceAction(action)
                            pendingAction = null
                        }) { Text("Continue") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            pendingAction = null
                            voiceStatus = "Action cancelled."
                        }) { Text("Cancel") }
                    }
                )
            }

            if (showAutomation) {
                AlertDialog(
                    onDismissRequest = { showAutomation = false },
                    title = { Text("Automation Center") },
                    text = {
                        Column(
                            modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Create local phone reminders. One-time and daily reminders are supported.", fontSize = 12.sp, color = Color.Gray)
                            OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, label = { Text("Reminder title") }, singleLine = true)
                            OutlinedTextField(value = reminderDelay, onValueChange = { reminderDelay = it.filter(Char::isDigit).take(5) }, label = { Text("Remind after (minutes)") }, singleLine = true)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Repeat every day", modifier = Modifier.weight(1f), color = Color.White)
                                Switch(checked = reminderDaily, onCheckedChange = { reminderDaily = it })
                            }
                            Button(
                                onClick = {
                                    val delay = reminderDelay.toLongOrNull()
                                    if (reminderTitle.isNotBlank() && delay != null && delay > 0) {
                                        addReminder(reminderTitle, delay, reminderDaily)
                                        reminders = loadReminders()
                                        reminderTitle = ""
                                        reminderDelay = "15"
                                    }
                                },
                                enabled = reminderTitle.isNotBlank() && (reminderDelay.toLongOrNull() ?: 0L) > 0
                            ) { Text("Add reminder") }
                            Divider()
                            if (reminders.isEmpty()) Text("No reminders yet.", color = Color.Gray)
                            reminders.forEach { reminder ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20243A))) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(reminder.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Text("After ${reminder.delayMinutes} min • ${if (reminder.daily) "Daily" else "Once"}", color = Color.Gray, fontSize = 11.sp)
                                        }
                                        Switch(
                                            checked = reminder.enabled,
                                            onCheckedChange = {
                                                setReminderEnabled(reminder.id, it)
                                                reminders = loadReminders()
                                            }
                                        )
                                        TextButton(onClick = {
                                            deleteReminder(reminder.id)
                                            reminders = loadReminders()
                                        }) { Text("Delete", color = Color(0xFFFCA5A5)) }
                                    }
                                }
                            }
                            Text("Note: daily reminders repeat every 24 hours after the initial delay. Android may delay notifications due to battery restrictions.", color = Color.Gray, fontSize = 10.sp)
                        }
                    },
                    confirmButton = { TextButton(onClick = { showAutomation = false }) { Text("Close") } }
                )
            }

            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("ZAVI AI Settings") },
                    text = {
                        Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Four AI providers • keys encrypted on this device. Enable providers for automatic fallback.", fontSize = 12.sp, color = Color.Gray)
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20243A))) {
                                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("ZAVI Accessibility Agent", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Enable Android Accessibility only if you want ZAVI to help with screen taps, scrolling and typing. Actions must be requested by you.", color = Color.LightGray, fontSize = 12.sp)
                                    Button(onClick = {
                                        try {
                                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                        } catch (_: Exception) {
                                            voiceStatus = "Android Accessibility Settings తెరవలేకపోయాను."
                                        }
                                    }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Open Accessibility Settings")
                                    }
                                }
                            }
                            providers.forEachIndexed { index, config ->
                                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF20243A))) {
                                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(config.provider.title, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("Enable", color = Color.LightGray, fontSize = 11.sp)
                                                Switch(checked = config.enabled, onCheckedChange = { enabled -> providers = providers.toMutableList().also { it[index] = config.copy(enabled = enabled) } })
                                            }
                                        }
                                        OutlinedTextField(value = config.apiKey, onValueChange = { value -> providers = providers.toMutableList().also { it[index] = config.copy(apiKey = value) } }, label = { Text("API Key") }, singleLine = true)
                                        OutlinedTextField(value = config.model, onValueChange = { value -> providers = providers.toMutableList().also { it[index] = config.copy(model = value) } }, label = { Text("Model ID") }, singleLine = true)
                                        TextButton(onClick = { scope.launch {
                                            providerTestStatus = "Testing ${config.provider.title}…"
                                            providerTestStatus = try { testProvider(config.copy(apiKey = config.apiKey.trim())); "${config.provider.title}: Connection successful" }
                                            catch (e: Exception) { "${config.provider.title}: ${e.message ?: "Connection failed"}" }
                                        } }, enabled = config.apiKey.isNotBlank()) { Text("Test connection") }
                                    }
                                }
                            }
                            if (providerTestStatus.isNotBlank()) Text(providerTestStatus, color = Color(0xFF67E8F9), fontSize = 12.sp)
                            Text("Default model IDs are editable. Free-tier quotas and availability depend on each provider.", fontSize = 11.sp, color = Color(0xFF67E8F9))
                            OutlinedTextField(
                                value = memory,
                                onValueChange = { memory = it },
                                label = { Text("Your memory / preferences (optional)") },
                                minLines = 3,
                                maxLines = 4
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            saveProviders(providers)
                            saveMemory(memory)
                            showSettings = false
                        }) { Text("Save") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = {
                                lines.clear()
                                clearMessages()
                                showSettings = false
                            }) { Text("Clear chat") }
                            TextButton(onClick = {
                                memory = ""
                                saveMemory("")
                                clearMemory()
                                voiceStatus = "Saved memory cleared."
                            }) { Text("Clear memory") }
                            TextButton(onClick = { showSettings = false }) { Text("Close") }
                        }
                    }
                )
            }
        }
    }
}
