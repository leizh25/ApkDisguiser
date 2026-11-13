package me.leizh25.apkdisguiser

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.net.URL
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.leizh25.apkdisguiser.ui.theme.APKTheme
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date

open class MainActivity : ComponentActivity() {
    protected open val floatingSearch = false

    private val overlayPermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Settings.canDrawOverlays(this)) startFloatingButton()
        else message = "请允许在其他应用上层显示"
    }

    private fun startFloatingButton(showMessage: Boolean = true) {
        androidx.core.content.ContextCompat.startForegroundService(
            this, Intent(this, FloatingSearchService::class.java)
        )
        if (showMessage) message = "悬浮按钮已开启"
    }

    private fun enableFloatingButton() {
        if (Settings.canDrawOverlays(this)) {
            startFloatingButton()
        } else {
            try {
                overlayPermission.launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(packageName)))
            } catch (_: android.content.ActivityNotFoundException) {
                message = "设备无法打开悬浮窗权限设置"
            }
        }
    }
    private val catalog = Catalog()
    private val storage by lazy { getSharedPreferences("installations", MODE_PRIVATE) }
    private var history by mutableStateOf(emptyList<String>())
    private var embedIcon by mutableStateOf(false)
    private var rootGranted by mutableStateOf(false)
    private var uninstallTarget by mutableStateOf<CatalogApp?>(null)
    private var records by mutableStateOf(emptyList<Record>())
    private var revision by mutableIntStateOf(0)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private val installer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        busy = false
    }

    private val permissionScreen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val selected = storage.getString("permissionSelection", null)?.let(::JSONObject)
        storage.edit().remove("permissionSelection").apply()
        if (selected != null && packageManager.canRequestPackageInstalls()) {
            install(CatalogApp(selected.getString("id"), selected.getString("name"), selected.optString("icon")))
        } else {
            busy = false
            message = "请先允许此应用安装未知应用"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        embedIcon = storage.getBoolean("embedIcon", false)
        if (savedInstanceState == null) {
            storage.edit().remove("permissionSelection").apply()
        }
        busy = storage.contains("permissionSelection")
        if (floatingSearch) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        } else {
            enableEdgeToEdge()
        }
        lifecycleScope.launch {
            rootGranted = withContext(Dispatchers.IO) { RootPackages.available() }
        }
        setContent { APKTheme { Home() } }
        if (!floatingSearch && savedInstanceState == null && Settings.canDrawOverlays(this)) {
            startFloatingButton(showMessage = false)
        }
    }

    override fun onResume() {
        super.onResume()
        embedIcon = storage.getBoolean("embedIcon", false)
        refresh()
    }

    private fun refresh() {
        records = storage.all.filterKeys { it.startsWith("record:") }.values.map { value ->
            val json = JSONObject(value as String)
            Record(CatalogApp(json.getString("id"), json.getString("name"), json.optString("icon")), json.getLong("date"))
        }.sortedByDescending { it.date }
        if (!storage.contains("searchHistory")) {
            saveHistory(records.map { it.app.name })
        } else {
            val saved = org.json.JSONArray(storage.getString("searchHistory", "[]"))
            history = List(saved.length()) { saved.getString(it) }
        }
        val pending = storage.getString("pending", null)?.let(::JSONObject)
        if (pending != null && confirmPending(pending)) {
            refresh()
            return
        }
        revision++
    }

    private fun saveHistory(names: List<String>) {
        history = names.distinct().take(20)
        storage.edit().putString("searchHistory", org.json.JSONArray(history).toString()).apply()
    }

    private fun confirmPending(pending: JSONObject): Boolean {
        val id = pending.getString("id")
        if (!isShellInstalled(id, Signature(pending.getString("certificate")))) return false
        val record = JSONObject().put("id", id).put("name", pending.getString("name"))
            .put("date", System.currentTimeMillis()).put("icon", pending.optString("icon"))
        val saved = if (storage.contains("searchHistory")) history else records.map { it.app.name }
        saveHistory(listOf(record.getString("name")) + saved)
        storage.edit().putString("record:$id", record.toString()).apply()
        storage.edit().remove("pending").apply()
        return true
    }

    @Suppress("DEPRECATION")
    private fun isShellInstalled(id: String, certificate: Signature): Boolean = try {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val info = packageManager.getPackageInfo(id, flags)
        val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        signers?.singleOrNull() == certificate
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun installed(id: String): Boolean = try {
        packageManager.getPackageInfo(id, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private fun install(app: CatalogApp) {
        if (installed(app.id)) {
            busy = false
            refresh()
            return
        }
        busy = true
        val data = JSONObject().put("id", app.id).put("name", app.name).put("icon", app.iconUrl)
        val useRoot = rootGranted
        val includeIcon = embedIcon
        if (!useRoot && Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            storage.edit().putString("permissionSelection", data.toString()).apply()
            try {
                permissionScreen.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri(packageName)))
            } catch (_: Exception) {
                storage.edit().remove("permissionSelection").apply()
                busy = false
                message = "无法打开未知应用安装设置"
            }
            return
        }
        lifecycleScope.launch {
            var generated: File? = null
            try {
                val (apk, certificate) = withContext(Dispatchers.IO) {
                    val key = ShellApk.signingKey(this@MainActivity)
                    val icon = if (includeIcon && app.iconUrl.isNotBlank()) fetchIcon(app.iconUrl) else null
                    ShellApk.create(this@MainActivity, app, key, icon) to Signature(key.certificate.encoded)
                }
                generated = apk

                data.put("certificate", certificate.toCharsString())
                check(storage.edit().putString("pending", data.toString()).commit())
                if (useRoot) {
                    withContext(Dispatchers.IO) { RootPackages.install(apk) }
                    apk.delete()
                    refresh()
                    busy = false
                    message = "安装成功"
                    return@launch
                }
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.apks", apk)
                installer.launch(Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .apply { clipData = android.content.ClipData.newRawUri("APK", uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                android.util.Log.e("ShellApk", "Could not generate or install shell", exception)
                storage.edit().remove("pending").apply()
                generated?.delete()
                busy = false
                message = "无法生成或启动安装：${exception.localizedMessage ?: "未知错误"}"
            }
        }
    }

    private fun packageUri(id: String): Uri = Uri.fromParts("package", id, null)

    private fun open(app: CatalogApp) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(app.id)
            if (intent == null) message = "该应用没有可打开的界面" else startActivity(intent)
        } catch (_: Exception) {
            message = "无法打开应用"
        }
    }

    private fun uninstall(app: CatalogApp) {
        if (rootGranted) {
            uninstallTarget = app
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_DELETE, packageUri(app.id)))
        } catch (_: Exception) {
            message = "无法打开系统卸载界面"
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun Home() {
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var selectedTab by rememberSaveable { mutableIntStateOf(0) }
        val tab = if (floatingSearch) 0 else selectedTab
        val searchFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            if (tab == 0) {
                searchFocus.requestFocus()
                keyboard?.show()
            }
        }
        var query by rememberSaveable { mutableStateOf("") }
        var retry by remember { mutableIntStateOf(0) }
        var results by remember { mutableStateOf(emptyList<CatalogApp>()) }
        var status by remember { mutableStateOf(SearchStatus.Idle) }
        LaunchedEffect(message) {
            val text = message ?: return@LaunchedEffect
            android.widget.Toast.makeText(this@MainActivity, text, android.widget.Toast.LENGTH_SHORT).show()
            message = null
        }
        LaunchedEffect(query.trim(), retry) {
            val keyword = query.trim()
            results = emptyList()
            status = if (keyword.isEmpty()) SearchStatus.Idle else SearchStatus.Loading
            if (keyword.isNotEmpty()) {
                delay(350)
                try {
                    results = withContext(Dispatchers.IO) { catalog.search(keyword) }
                    status = SearchStatus.Ready
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    status = SearchStatus.Error
                }
            }
        }
        val recordList = records
        val installedIds = remember(results, recordList, revision) {
            (results + recordList.map { it.app }).map { it.id }.distinct().filter(::installed).toSet()
        }
        uninstallTarget?.let { app ->
            AlertDialog(
                onDismissRequest = { uninstallTarget = null },
                title = { Text("确认卸载") },
                text = {
                    Text("名称：${app.name}\n包名：${app.id}")
                },
                dismissButton = { TextButton(onClick = { uninstallTarget = null }) { Text("取消") } },
                confirmButton = {
                    TextButton(onClick = {
                        uninstallTarget = null
                        busy = true
                        lifecycleScope.launch {
                            try {
                                withContext(Dispatchers.IO) { RootPackages.uninstall(app.id) }
                                message = "卸载成功"
                            } catch (exception: CancellationException) {
                                throw exception
                            } catch (exception: Exception) {
                                message = "卸载失败：${exception.localizedMessage}"
                            } finally {
                                busy = false
                                refresh()
                            }
                        }
                    }) { Text("卸载") }
                }
            )
        }

        if (showSettings && !floatingSearch) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("设置") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("嵌入 APP 图标", Modifier.weight(1f))
                            Switch(checked = embedIcon, onCheckedChange = { checked ->
                                embedIcon = checked
                                storage.edit().putBoolean("embedIcon", checked).apply()
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("悬浮窗", Modifier.weight(1f))
                            Switch(checked = FloatingSearchService.isShowing, onCheckedChange = { checked ->
                                if (checked) {
                                    enableFloatingButton()
                                } else {
                                    stopService(Intent(this@MainActivity, FloatingSearchService::class.java))
                                }
                            })
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showSettings = false }) { Text("完成") } }
            )
        }
        Scaffold(
            modifier = if (floatingSearch) Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 450.dp) else Modifier,
            topBar = {
                TopAppBar(title = { Text(if (floatingSearch) "搜索应用" else "APK 伪装器") }, actions = {
                    if (floatingSearch) {
                        TextButton(onClick = { finish() }) { Text("关闭") }
                    } else {
                        TextButton(onClick = { keyboard?.hide(); showSettings = true }) { Text("设置") }
                    }
                })
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (!floatingSearch) PrimaryTabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { selectedTab = 0 }, text = { Text("搜索应用") })
                    Tab(tab == 1, { selectedTab = 1 }, text = { Text("安装记录") })
                }
                if (tab == 0) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().padding(16.dp).focusRequester(searchFocus),
                        label = { Text("搜索应用名称") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } }
                    )
                }
                if (tab == 0 && !floatingSearch && results.isEmpty() && history.isNotEmpty()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("安装历史", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { saveHistory(emptyList()) }) { Text("清除安装历史") }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        history.forEach { name ->
                            Text(
                                text = name,
                                modifier = Modifier.widthIn(max = 220.dp)
                                    .clickable { query = name }
                                    .padding(vertical = 4.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (busy || tab == 0 && status == SearchStatus.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (tab == 0 && results.isNotEmpty() || tab == 1 && recordList.isNotEmpty()) {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (tab == 0) {
                            items(results, key = { it.id }) { app ->
                                AppRow(app, app.id in installedIds)
                            }
                        } else {
                            items(recordList, key = { it.app.id }) { record ->
                                AppRow(record.app, record.app.id in installedIds, record.date)
                            }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(when {
                            tab == 1 -> "暂无安装记录"
                            status == SearchStatus.Loading -> "正在搜索…"
                            status == SearchStatus.Error -> "搜索失败，请重试"
                            status == SearchStatus.Ready -> "没有找到匹配的应用"
                            else -> "输入关键字搜索应用"
                        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (tab == 0 && status == SearchStatus.Error) {
                            TextButton(onClick = { retry++ }) { Text("重试") }
                        }
                    }
                }
            }
        }
    }

    private val icons = LruCache<String, Bitmap>(64)

    private fun fetchIcon(rawUrl: String): Bitmap? {
        icons.get(rawUrl)?.let { return it }
        return runCatching {
            val url = URL(rawUrl.replaceFirst("http://", "https://"))
            if (url.protocol != "https" || url.host != "pp.myapp.com") return null
            val connection = url.openConnection() as javax.net.ssl.HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 4000
                connection.readTimeout = 4000
                val data = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (output.size() <= 512 * 1024) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                if (data.size > 512 * 1024) return null
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                if (options.outWidth !in 1..1024 || options.outHeight !in 1..1024) return null
                options.inJustDecodeBounds = false
                options.inSampleSize = (maxOf(options.outWidth, options.outHeight) / 128).coerceAtLeast(1)
                BitmapFactory.decodeByteArray(data, 0, data.size, options)?.also { icons.put(rawUrl, it) }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    @Composable
    private fun AppRow(app: CatalogApp, installed: Boolean, installedAt: Long? = null) {
        val bitmap by produceState<Bitmap?>(null, app.iconUrl) {
            value = null
            if (app.iconUrl.isNotBlank()) value = withContext(Dispatchers.IO) { fetchIcon(app.iconUrl) }
        }
        val shape = RoundedCornerShape(10.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = bitmap
            if (icon != null) {
                Image(icon.asImageBitmap(), contentDescription = null, Modifier.size(36.dp).clip(shape))
            } else {
                Surface(Modifier.size(36.dp), shape = shape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(app.name.take(1), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(app.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall)
                Text(app.id, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (installedAt != null) {
                    val time = remember(installedAt) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(installedAt))
                    }
                    Text("${if (installed) "已安装" else "已卸载"} · $time",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (installed) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val buttonPadding = PaddingValues(horizontal = 8.dp)
            if (installed) {
                FilledTonalButton(onClick = { open(app) }, enabled = !busy,
                    modifier = Modifier.width(56.dp), contentPadding = buttonPadding) { Text("打开") }
                if (app.id != packageName) {
                    TextButton(onClick = { uninstall(app) }, enabled = !busy,
                        modifier = Modifier.width(52.dp), contentPadding = buttonPadding) { Text("卸载") }
                }
            } else if (installedAt == null) {
                FilledTonalButton(onClick = { install(app) }, enabled = !busy,
                    modifier = Modifier.width(56.dp), contentPadding = buttonPadding) { Text("安装") }
            }
        }
        HorizontalDivider(Modifier.padding(start = 58.dp, end = 12.dp))
    }

    private data class Record(val app: CatalogApp, val date: Long)
    private enum class SearchStatus { Idle, Loading, Ready, Error }
}
