package com.huimaotech.invoicevault

import android.content.*
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); enableEdgeToEdge(); setContent { VaultTheme { VaultScreen() } }; handle(intent) }
    override fun onNewIntent(i: Intent) { super.onNewIntent(i); handle(i) }
    private fun handle(i: Intent) {
        if (i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE) {
            val uris = if (i.action == Intent.ACTION_SEND_MULTIPLE) i.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty() else listOfNotNull(i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java))
            (application as VaultApp).let { app -> lifecycleScope.launch { val repo = VaultRepository(this@MainActivity, app.db.dao()); uris.take(20).forEach { runCatching { repo.import(it) } } } }
        }
    }
}

class VaultVm(private val app: VaultApp) : ViewModel() {
    val dao = app.db.dao(); val repo = VaultRepository(app, dao)
    val rows = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val totals = dao.observeTotals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Totals(0, 0, 0, 0))
    var message by mutableStateOf(""); var selected by mutableStateOf<InvoiceEntity?>(null); var recognizingIds by mutableStateOf(setOf<Long>()); var batchProgress by mutableStateOf(""); var categoryProgress by mutableStateOf("")
    val credentialStore = CredentialStore(app); var webDavConfig by mutableStateOf(credentialStore.load() ?: WebDavConfig()); var webDavBusy by mutableStateOf(""); var webDavProgress by mutableStateOf(""); var webDavLastStatus by mutableStateOf(""); var remoteBackups by mutableStateOf<List<RemoteBackup>>(emptyList())
    private val secureBackup = SecureBackup(app, app.db)
    fun imports(us: List<Uri>) = viewModelScope.launch { val results = us.take(50).map { runCatching { repo.import(it) }.fold({ r -> "${r.name}: ${r.outcome}" }, { e -> "失败: ${e.message}" }) }; message = results.joinToString("\n") }
    fun reRecognize(id: Long) = viewModelScope.launch { recognizingIds += id; runCatching { repo.reRecognize(id) }.onSuccess { r -> selected = r.invoice; message = r.outcome }.onFailure { message = it.message ?: "重新识别失败" }; recognizingIds -= id }
    fun batchReRecognize(items: List<InvoiceEntity>) = viewModelScope.launch { val targets = items.filter { RecognitionPolicy.shouldReRecognize(it) && repo.originalExists(it) }; var ok = 0; targets.forEachIndexed { i, v -> batchProgress = "正在重新识别 ${i + 1}/${targets.size}"; recognizingIds += v.id; runCatching { repo.reRecognize(v.id) }.onSuccess { ok++ }; recognizingIds -= v.id }; batchProgress = ""; message = "批量重新识别完成：成功 $ok/${targets.size} 张" }
    fun batchClassify(items:List<InvoiceEntity>)=viewModelScope.launch{val targets=CategoryEngine.batchTargets(items);var ok=0;targets.forEachIndexed{i,v->categoryProgress="分类中 ${i+1}/${targets.size}";runCatching{repo.autoClassify(v)}.onSuccess{ok++}};categoryProgress="";message="自动分类完成：成功 $ok/${targets.size} 张"}
    fun reclassify(v:InvoiceEntity)=viewModelScope.launch{runCatching{repo.autoClassify(v,true)}.onSuccess{selected=it;message="已重新自动分类为 ${Category.fromId(it.categoryId)?.title}"}.onFailure{message=it.message?:"分类失败"}}
    fun chooseCategory(v:InvoiceEntity,c:Category,remember:Boolean)=viewModelScope.launch{runCatching{repo.setCategory(v,c,remember)}.onSuccess{selected=it;message="已分类为 ${c.title}"}.onFailure{message=it.message?:"保存分类失败"}}
    fun save(v: InvoiceEntity) = viewModelScope.launch { var saved=v.copy(identityKey = InvoiceLogic.identity(v.code, v.number, v.sellerTaxId), status = "已确认");if(saved.categorySource!=CategorySource.MANUAL.name){val rule=CategoryEngine.normalizeSeller(saved.seller).takeIf{it.isNotBlank()}?.let{dao.rule(it)};saved=CategoryEngine.applyUnlessManual(saved,CategoryEngine.classify(saved.itemContent,saved.seller,saved.rawText,rule))};dao.update(saved); selected = saved }
    fun delete(v: InvoiceEntity, done: (Boolean) -> Unit = {}) = viewModelScope.launch { runCatching { dao.delete(v); repo.deleteFile(v) }.onSuccess { selected = null; done(true) }.onFailure { message = "删除失败：${it.message}"; done(false) } }
    fun saveWebDav(c: WebDavConfig) = runCatching { val normalized = c.copy(serverUrl = WebDavRules.normalizeUrl(c.serverUrl, c.allowHttp), remoteDir = WebDavRules.normalizePath(c.remoteDir)); credentialStore.save(normalized); webDavConfig = normalized; webDavLastStatus = "设置已保存"; message = "WebDAV 配置已加密保存" }.onFailure { message = it.message ?: "配置无效" }
    fun clearWebDav() { credentialStore.clear(); webDavConfig = WebDavConfig(); remoteBackups = emptyList(); webDavLastStatus = ""; message = "WebDAV 配置与本机密钥已清除" }
    fun testWebDav() = webDav("连接中") { WebDavClient(webDavConfig).ensureDirectory(); webDavLastStatus = "连接正常"; message = "连接成功，远端目录可用" }
    fun refreshWebDav() = webDav("正在刷新") { remoteBackups = WebDavClient(webDavConfig).list(); webDavLastStatus = "最近刷新成功"; message = "找到 ${remoteBackups.size} 个远端备份" }
    fun uploadWebDav() = webDav("上传中") { val f = secureBackup.create(); WebDavClient(webDavConfig).upload(f) { d, t -> webDavProgress = if (t > 0) "${d * 100 / t}%" else "${d / 1024} KB" }; remoteBackups = WebDavClient(webDavConfig).list(); webDavLastStatus = "最近备份成功"; message = "备份已上传：${f.name}" }
    fun restoreWebDav(item: RemoteBackup) = webDav("下载中") { val f = File(app.cacheDir, "restore-${System.nanoTime()}.zip"); try { WebDavClient(webDavConfig).download(item, f) { d, t -> webDavProgress = if (t > 0) "${d * 100 / t}%" else "${d / 1024} KB" }; webDavBusy = "校验并恢复中"; val r = secureBackup.validateAndRestore(f); webDavLastStatus = "最近恢复成功"; message = "完整恢复成功（${r.added} 个原件）。请立即重启应用以安全重建数据库连接。" } finally { f.delete() } }
    private fun webDav(label: String, block: suspend () -> Unit) = viewModelScope.launch { if (webDavBusy.isNotBlank()) return@launch; webDavBusy = label; webDavProgress = ""; runCatching { withContext(Dispatchers.IO) { block() } }.onFailure { webDavLastStatus = "最近操作失败"; message = it.message ?: "操作失败" }; webDavBusy = ""; webDavProgress = "" }
}

@Composable fun VaultTheme(content: @Composable () -> Unit) {
    val c = LocalContext.current; val dark = isSystemInDarkTheme()
    val scheme = if (android.os.Build.VERSION.SDK_INT >= 31) { if (dark) dynamicDarkColorScheme(c) else dynamicLightColorScheme(c) } else if (dark) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(18.dp), medium = RoundedCornerShape(26.dp), large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(40.dp)), content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun VaultScreen() {
    val app = LocalContext.current.applicationContext as VaultApp
    val vm: VaultVm = viewModel(factory = object : ViewModelProvider.Factory { override fun <T : ViewModel> create(c: Class<T>): T = VaultVm(app) as T })
    val rows by vm.rows.collectAsStateWithLifecycle(); val totals by vm.totals.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }; var filter by rememberSaveable { mutableStateOf("全部") }; var categoryFilter by rememberSaveable { mutableStateOf("") }
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val prefs = ctx.getSharedPreferences("settings", 0); var email by rememberSaveable { mutableStateOf(prefs.getString("email", "").orEmpty()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.imports(it) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u -> if (u != null) scope.launch { runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(u, "w")!!.use { vm.repo.exportTo(it) } } }.onSuccess { vm.message = "ZIP 已保存" }.onFailure { vm.message = "导出失败：${it.message}" } } }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { if (vm.message.isNotBlank()) { snackbar.showSnackbar(vm.message, withDismissAction = true, duration = SnackbarDuration.Long); vm.message = "" } }
    val filtered = remember(rows, filter, categoryFilter, query) { rows.filter { (filter == "全部" || it.status.contains(filter)) && (categoryFilter.isBlank() || it.categoryId==categoryFilter) && (query.isBlank() || listOf(it.number, it.code, it.seller, it.sellerTaxId,it.itemContent).any { s -> s.contains(query, true) }) } }

    val nav = rememberNavController(); val entry by nav.currentBackStackEntryAsState(); val route = entry?.destination?.route ?: VaultRoutes.OVERVIEW
    val rootRoutes = VaultRoutes.ROOTS; val isRoot = route in rootRoutes; val selectedRoot = rootRoutes.indexOf(route).coerceAtLeast(0)
    fun root(r: String) { nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
    val roots = listOf("概览" to Icons.Rounded.Home, "发票" to Icons.Rounded.ReceiptLong, "设置" to Icons.Rounded.Settings)

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { if (isRoot) CenterAlignedTopAppBar(title = { Text(roots[selectedRoot].first, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) }) },
        bottomBar = { if (isRoot) NavigationBar { roots.forEachIndexed { i, x -> NavigationBarItem(selectedRoot == i, { root(rootRoutes[i]) }, icon = { Icon(x.second, null) }, label = { Text(x.first) }) } } },
        floatingActionButton = { if (isRoot && route != VaultRoutes.SETTINGS) ExtendedFloatingActionButton(onClick = { picker.launch(arrayOf("application/pdf", "application/xml", "text/xml", "image/jpeg", "image/png")) }, icon = { Icon(Icons.Rounded.Add, "导入发票") }, text = { Text("导入") }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = VaultRoutes.OVERVIEW,
            modifier = Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize(),
            enterTransition = { fadeIn(tween(140)) + slideInHorizontally(tween(180)) { it / 12 } },
            exitTransition = { fadeOut(tween(100)) },
            popEnterTransition = { fadeIn(tween(100)) },
            popExitTransition = { fadeOut(tween(140)) + slideOutHorizontally(tween(180)) { it / 12 } }
        ) {
            composable(VaultRoutes.OVERVIEW) { BoxWithConstraints(Modifier.fillMaxSize()) { Overview(totals, rows, maxWidth >= 700.dp, vm.batchProgress, { vm.batchReRecognize(rows) }, vm.categoryProgress, { vm.batchClassify(rows) }, { status -> filter = status; root(VaultRoutes.INVOICES) }, { invoice -> vm.selected = invoice; nav.navigate(VaultRoutes.invoice(invoice.id)) }) } }
            composable(VaultRoutes.INVOICES) { BoxWithConstraints(Modifier.fillMaxSize()) { InvoiceList(filtered, query, { query = it }, filter, { filter = it }, categoryFilter, { categoryFilter = it }, { invoice -> vm.selected = invoice; nav.navigate(VaultRoutes.invoice(invoice.id)) }, maxWidth >= 700.dp) } }
            composable(VaultRoutes.SETTINGS) { SettingsHome(email, vm) { nav.navigate(VaultRoutes.forSetting(it)) } }
            composable(VaultRoutes.INVOICE) { back ->
                val id = back.arguments?.getString("id")?.toLongOrNull(); val detail = vm.selected?.takeIf { it.id == id } ?: rows.firstOrNull { it.id == id }; var editing by rememberSaveable(id) { mutableStateOf(false) }
                if (detail != null) {
                    InvoiceDetail(detail, { nav.popBackStack() }, { editing = true }, { vm.delete(detail) { deleted -> if (deleted) nav.popBackStack() } }, { vm.reRecognize(detail.id) }, { vm.reclassify(detail) }, { c,r -> vm.chooseCategory(detail,c,r) }, detail.id in vm.recognizingIds, { vm.message = it })
                    if (editing) EditDialog(detail, { editing = false }, { editing = false; vm.save(it) }, {})
                } else Box(Modifier.fillMaxSize()) { LaunchedEffect(id) { vm.message = "发票不存在或已删除"; nav.popBackStack() } }
            }
            composable(VaultRoutes.WEBDAV) { WebDavSettingsScreen(vm) { nav.popBackStack() } }
            composable(VaultRoutes.EXPORT) { ExportEmailScreen(email, { email = it; prefs.edit().putString("email", it).apply() }, { create.launch("invoice-vault-${System.currentTimeMillis()}.zip") }, { share(vm, ctx, email) }) { nav.popBackStack() } }
            composable(VaultRoutes.PRIVACY) { PrivacyDataScreen { nav.popBackStack() } }
            composable(VaultRoutes.ABOUT) { AboutScreen({ nav.popBackStack() }) { vm.message = it } }
        }
    }
}

fun share(vm: VaultVm, ctx: Context, email: String) {
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }; val file = File(dir, "invoice-vault.zip")
    vm.viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { file.outputStream().use { vm.repo.exportTo(it) } }; ctx.startActivity(Intent.createChooser(vm.repo.shareExport(file, email), "选择邮件应用")) }.onFailure { vm.message = "准备附件失败：${it.message}" } }
}
fun money(v: Long?) = "¥%.2f".format((v ?: 0) / 100.0)
