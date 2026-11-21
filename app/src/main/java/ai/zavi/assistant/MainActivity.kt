package ai.zavi.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val client = OkHttpClient()
    private val prefs by lazy {
        val key = MasterKey.Builder(this).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(this, "zavi_secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ZaviApp(
            getKey = { prefs.getString("openrouter_key", "") ?: "" },
            saveKey = { prefs.edit().putString("openrouter_key", it).apply() },
            ask = { prompt -> withContext(Dispatchers.IO) {
                val token = prefs.getString("openrouter_key", "") ?: ""
                check(token.isNotBlank()) { "Settingsలో OpenRouter API key add చేయండి." }
                val messages = JSONArray()
                    .put(JSONObject().put("role","system").put("content","You are ZAVI AI, a helpful bilingual Telugu and English assistant. Reply in the user's language."))
                    .put(JSONObject().put("role","user").put("content",prompt))
                val body = JSONObject().put("model","openrouter/auto").put("messages",messages).toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("https://openrouter.ai/api/v1/chat/completions")
                    .addHeader("Authorization","Bearer $token")
                    .addHeader("HTTP-Referer","https://github.com/ygupthapawan173-cmd/ZAVI-AI")
                    .addHeader("X-Title","ZAVI AI").post(body).build()
                client.newCall(req).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    check(response.isSuccessful) { "OpenRouter error: $raw" }
                    JSONObject(raw).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
            } }
        ) }
    }
}

private data class ChatLine(val text:String, val user:Boolean)
@Composable
private fun ZaviApp(getKey:()->String, saveKey:(String)->Unit, ask:suspend (String)->String) {
    var prompt by remember { mutableStateOf("") }
    var key by remember { mutableStateOf(getKey()) }
    var showSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf(ChatLine("నమస్కారం! నేను ZAVI AI. ఎలా సహాయం చేయాలి?",false)) }
    val scope = rememberCoroutineScope()
    val bg = Brush.verticalGradient(listOf(Color(0xFF080B18),Color(0xFF17102B),Color(0xFF080B18)))
    MaterialTheme(colorScheme = darkColorScheme(primary=Color(0xFF8B5CF6), secondary=Color(0xFF22D3EE), background=Color(0xFF080B18))) {
        Box(Modifier.fillMaxSize().background(bg).padding(16.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth()) {
                    Column {
                        Text("ZAVI AI",fontSize=28.sp,color=Color.White)
                        Text("Your bilingual AI companion",fontSize=12.sp,color=Color(0xFF9CA3AF))
                    }
                    TextButton(onClick={showSettings=true}) { Text("Settings",color=Color(0xFF67E8F9)) }
                }
                Spacer(Modifier.height(14.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    items(lines) { line ->
                        Box(Modifier.fillMaxWidth(),contentAlignment=if(line.user) Alignment.CenterEnd else Alignment.CenterStart) {
                            Text(line.text,Modifier.widthIn(max=310.dp).background(if(line.user) Color(0xFF49318A) else Color(0xFF20243A),RoundedCornerShape(18.dp)).padding(14.dp),color=Color.White)
                        }
                    }
                    if(busy) item { Text("ZAVI is thinking…",color=Color(0xFF67E8F9)) }
                }
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().background(Color(0xFF20243A),RoundedCornerShape(24.dp)).padding(horizontal=14.dp, vertical=10.dp)) {
                    BasicTextField(value=prompt,onValueChange={prompt=it},modifier=Modifier.weight(1f),textStyle=TextStyle(color=Color.White,fontSize=16.sp),decorationBox={ inner -> if(prompt.isEmpty()) Text("Message ZAVI…",color=Color.Gray); inner() })
                    IconButton(onClick={
                        val q=prompt.trim()
                        if(q.isNotEmpty() && !busy) {
                            lines.add(ChatLine(q,true)); prompt=""; busy=true
                            scope.launch { try { lines.add(ChatLine(ask(q),false)) } catch(e:Exception) { lines.add(ChatLine(e.message ?: "Request failed",false)) } finally { busy=false } }
                        }
                    },enabled=!busy) { Icon(Icons.Default.Send,contentDescription="Send",tint=Color(0xFF67E8F9)) }
                }
            }
            if(showSettings) AlertDialog(onDismissRequest={showSettings=false},title={Text("OpenRouter Settings")},text={
                Column {
                    Text("API key encrypted on this device.",fontSize=12.sp,color=Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value=key,onValueChange={key=it},label={Text("OpenRouter API Key")},singleLine=true)
                }
            },confirmButton={TextButton(onClick={saveKey(key);showSettings=false}) { Text("Save") }},dismissButton={TextButton(onClick={showSettings=false}) { Text("Cancel") }})
        }
    }
}
