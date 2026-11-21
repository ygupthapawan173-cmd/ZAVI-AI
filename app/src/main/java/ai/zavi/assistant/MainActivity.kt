package ai.zavi.assistant

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import ai.zavi.assistant.data.ConversationStore
import ai.zavi.assistant.models.ChatMessage
import ai.zavi.assistant.network.OpenRouterClient
import kotlinx.coroutines.launch
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
    private val openRouterClient by lazy { OpenRouterClient() }
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
                getKey = { securePrefs.getString(KEY_OPENROUTER, "") ?: "" },
                saveKey = { securePrefs.edit().putString(KEY_OPENROUTER, it.trim()).apply() },
                getModel = { securePrefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL },
                saveModel = { securePrefs.edit().putString(KEY_MODEL, it.trim()).apply() },
                loadMemory = { conversationStore.loadMemory() },
                saveMemory = { conversationStore.saveMemory(it) },
                loadMessages = { conversationStore.loadMessages() },
                saveMessages = { conversationStore.saveMessages(it) },
                clearMessages = { conversationStore.clearMessages() },
                ask = { prompt, history, memory, model ->
                    val token = securePrefs.getString(KEY_OPENROUTER, "") ?: ""
                    val (state, messages) = AgentWorkflow.prepare(prompt, history, memory)
                    val answer = openRouterClient.complete(token, model, messages)
                    AgentReply(answer, state.selectedAgent.title)
                },
                launchDeviceAction = { action -> DeviceActionLauncher.launch(this, action) },
                startVoice = { onResult, onError -> startVoiceRecognition(onResult, onError) },
                speak = { speakText(it) }
            )
        }
    }

    private fun startVoiceRecognition(
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onError("ఈ ఫోన్‌లో speech recognition అందుబాటులో లేదు.")
            return
        }

        speechResult = onResult
        speechError = onError

        try {
            val recognizer = speechRecognizer
                ?: SpeechRecognizer.createSpeechRecognizer(this).also { created ->
                    created.setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) = Unit
                        override fun onBeginningOfSpeech() = Unit
                        override fun onRmsChanged(rmsdB: Float) = Unit
                        override fun onBufferReceived(buffer: ByteArray?) = Unit
                        override fun onEndOfSpeech() = Unit
                        override fun onEvent(eventType: Int, params: Bundle?) = Unit
                        override fun onPartialResults(partialResults: Bundle?) = Unit

                        override fun onError(error: Int) {
                            speechError?.invoke("Voice recognition failed (code $error). మళ్లీ ప్రయత్నించండి.")
                            speechResult = null
                            speechError = null
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
                        }
                    })
                }
            speechRecognizer = recognizer

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            recognizer.startListening(intent)
        } catch (error: Exception) {
            onError(error.message ?: "Voice input ప్రారంభం కాలేదు.")
        }
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
    getKey: () -> String,
    saveKey: (String) -> Unit,
    getModel: () -> String,
    saveModel: (String) -> Unit,
    loadMemory: () -> String,
    saveMemory: (String) -> Unit,
    loadMessages: () -> List<ChatMessage>,
    saveMessages: (List<ChatMessage>) -> Unit,
    clearMessages: () -> Unit,
    ask: suspend (String, List<ChatMessage>, String, String) -> AgentReply,
    launchDeviceAction: (DeviceAction) -> String,
    startVoice: ((String) -> Unit, (String) -> Unit) -> Unit,
    speak: (String) -> Unit
) {
    var prompt by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(getKey()) }
    var model by remember { mutableStateOf(getModel()) }
    var memory by remember { mutableStateOf(loadMemory()) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var voiceReplies by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<DeviceAction?>(null) }
    val lines = remember { mutableStateListOf<ChatMessage>().apply { addAll(loadMessages()) } }
    val scope = rememberCoroutineScope()
    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            voiceStatus = "వింటున్నాను…"
            startVoice(
                { recognized ->
                    prompt = recognized
                    voiceStatus = "Voice input ready — Send నొక్కి కొనసాగించండి."
                },
                { error -> voiceStatus = error }
            )
        } else {
            voiceStatus = "Voice input కోసం microphone permission అవసరం."
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
                    Column {
                        Text("ZAVI AI", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Cloud intelligence • Telugu + English", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = { voiceReplies = !voiceReplies }) {
                            Text(if (voiceReplies) "Voice replies ON" else "Voice replies OFF", color = Color(0xFF67E8F9))
                        }
                        TextButton(onClick = {
                            key = getKey()
                            model = getModel()
                            memory = loadMemory()
                            showSettings = true
                        }) {
                            Text("Settings", color = Color(0xFF67E8F9))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
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
                            Text(
                                line.content,
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
                            if (query.isNotEmpty() && !busy) {
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
                                        lines.add(ChatMessage(role = "user", content = query))
                                        saveMessages(lines.toList())
                                        prompt = ""
                                        voiceStatus = ""
                                        busy = true
                                        scope.launch {
                                            try {
                                                val reply = ask(query, previousHistory, loadMemory(), getModel())
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

            if (showSettings) {
                AlertDialog(
                    onDismissRequest = { showSettings = false },
                    title = { Text("ZAVI AI Settings") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("API key and memory are encrypted on this device.", fontSize = 12.sp, color = Color.Gray)
                            OutlinedTextField(
                                value = key,
                                onValueChange = { key = it },
                                label = { Text("OpenRouter API Key") },
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = model,
                                onValueChange = { model = it },
                                label = { Text("Model") },
                                singleLine = true
                            )
                            Text("Free-first default: openrouter/free", fontSize = 11.sp, color = Color(0xFF67E8F9))
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
                            saveKey(key)
                            saveModel(model.ifBlank { "openrouter/free" })
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
                            TextButton(onClick = { showSettings = false }) { Text("Close") }
                        }
                    }
                )
            }
        }
    }
}
