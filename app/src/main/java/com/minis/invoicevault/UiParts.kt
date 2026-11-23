package com.minis.invoicevault

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

@Composable
fun Overview(t: Totals, rows: List<InvoiceEntity>, wide: Boolean, batchProgress: String, onBatch: () -> Unit,categoryProgress:String,onClassify:()->Unit, onStatus: (String) -> Unit, onInvoice: (InvoiceEntity) -> Unit) {
    val pending = rows.count { it.status.contains("待确认") }
    val duplicates = rows.count { it.status.contains("重复") }
    val recoverable = rows.count { RecognitionPolicy.shouldReRecognize(it) }
    val uncategorized=rows.count{CategoryEngine.uncategorized(it)}
    LazyColumn(
        modifier = Modifier.fillMaxSize().widthIn(max = 920.dp),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 112.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item(key = "intro", contentType = "header") { Column { Text("财务概览", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold); Text("你的本地发票数据一目了然", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        item(key = "hero", contentType = "hero") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column { Text("价税合计", style = MaterialTheme.typography.titleMedium); Text(money(t.total), style = tabular(MaterialTheme.typography.displayMedium), fontWeight = FontWeight.Bold) }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .16f))
                    Row(Modifier.fillMaxWidth()) { OverviewValue("发票", "${t.count} 张", Modifier.weight(1f)); OverviewValue("不含税", money(t.amount), Modifier.weight(1f)); OverviewValue("税额", money(t.tax), Modifier.weight(1f)) }
                }
            }
        }
        item(key = "actions-title", contentType = "title") { Text("需要留意", style = MaterialTheme.typography.titleLarge) }
        item(key = "actions", contentType = "group") {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column {
                    DashboardAction(Icons.Rounded.PendingActions, "待确认", "$pending 张需要核对", { onStatus("待确认") })
                    HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    DashboardAction(Icons.Rounded.ContentCopy, "重复提示", "$duplicates 张可能重复", { onStatus("重复") })
                }
            }
        }
        if (recoverable > 0) item(key = "repair", contentType = "banner") {
            Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(30.dp)); Column(Modifier.weight(1f)) { Text("可修复 $recoverable 张旧记录", style = MaterialTheme.typography.titleMedium); Text("使用新版端侧识别重新处理", color = MaterialTheme.colorScheme.onTertiaryContainer) }; FilledTonalButton(onBatch, enabled = batchProgress.isBlank()) { Text(if (batchProgress.isBlank()) "修复" else batchProgress) }
                }
            }
        }
        if(uncategorized>0)item(key="classify",contentType="banner"){Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.secondaryContainer){Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){Icon(Icons.Rounded.Category,null,Modifier.size(30.dp));Column(Modifier.weight(1f)){Text("有 $uncategorized 张未分类",style=MaterialTheme.typography.titleMedium);Text("完全离线，按顺序处理",color=MaterialTheme.colorScheme.onSecondaryContainer)};FilledTonalButton(onClassify,enabled=categoryProgress.isBlank()){Text(if(categoryProgress.isBlank())"一键分类" else categoryProgress)}}}}
        item(key = "recent-title", contentType = "title") { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("最近发票", style = MaterialTheme.typography.titleLarge); Text("${rows.size} 张", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        if (rows.isEmpty()) item(key = "empty", contentType = "empty") { EmptyInvoices("还没有发票", "点击下方“导入”添加 PDF、XML 或图片") }
        else item(key = "recent", contentType = "group") { InvoiceGroup(rows.take(5), onInvoice) }
    }
}

@Composable private fun OverviewValue(label: String, value: String, modifier: Modifier) { Column(modifier) { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer); Text(value, style = tabular(MaterialTheme.typography.titleMedium), maxLines = 1) } }
@Composable private fun DashboardAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) { Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Icon(icon, null, Modifier.padding(10.dp).size(24.dp)) }; Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Rounded.ChevronRight, null) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceList(rows: List<InvoiceEntity>, query: String, onQuery: (String) -> Unit, filter: String, onFilter: (String) -> Unit,categoryFilter:String,onCategoryFilter:(String)->Unit, onClick: (InvoiceEntity) -> Unit, wide: Boolean) {
    Column(Modifier.fillMaxSize().widthIn(max = 920.dp).padding(horizontal = 20.dp)) {
        Text("所有发票", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        SearchBar(inputField = { SearchBarDefaults.InputField(query = query, onQueryChange = onQuery, onSearch = {}, expanded = false, onExpandedChange = {}, placeholder = { Text("搜索号码、销方或税号") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if (query.isNotBlank()) IconButton({ onQuery("") }) { Icon(Icons.Rounded.Close, "清除搜索") } }) }, expanded = false, onExpandedChange = {}, modifier = Modifier.fillMaxWidth()) {}
        Row(
            Modifier.padding(vertical = 12.dp).horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("全部", "待确认", "重复").forEach { item ->
                FilterChip(
                    selected = filter == item,
                    onClick = { onFilter(item) },
                    label = { Text(item) },
                    leadingIcon = if (filter == item) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null
                )
            }
            FilterChip(
                selected = categoryFilter.isNotBlank(),
                onClick = { if (categoryFilter.isNotBlank()) onCategoryFilter("") },
                label = { Text(Category.fromId(categoryFilter)?.title ?: "分类") },
                leadingIcon = { Icon(Icons.Rounded.Category, null, Modifier.size(18.dp)) }
            )
            Category.entries.forEach { category ->
                val selected = categoryFilter == category.id
                val colors = CategoryColors(category)
                FilterChip(
                    selected = selected,
                    onClick = { onCategoryFilter(category.id) },
                    label = { Text(category.title) },
                    leadingIcon = if (selected) ({ Icon(categoryIcon(category), null, Modifier.size(18.dp)) }) else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.container,
                        selectedLabelColor = colors.content,
                        selectedLeadingIconColor = colors.content
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        selectedBorderColor = colors.accent,
                        selectedBorderWidth = 1.dp
                    )
                )
            }
        }
        if (rows.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyInvoices(if (query.isBlank() && filter == "全部") "还没有发票" else "没有匹配结果", if (query.isBlank()) "点击“导入”添加发票" else "试试其他关键词或筛选条件") }
        else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
            item(key = "group", contentType = "invoice_group") { InvoiceGroup(rows, onClick) }
        }
    }
}

@Composable private fun InvoiceGroup(rows: List<InvoiceEntity>, onClick: (InvoiceEntity) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column { rows.forEachIndexed { index, v -> if (index > 0) HorizontalDivider(Modifier.padding(start = 74.dp), color = MaterialTheme.colorScheme.outlineVariant); InvoiceRow(v) { onClick(v) } } }
    }
}
@Composable private fun InvoiceRow(v: InvoiceEntity, onClick: () -> Unit) {
    val cat = Category.fromId(v.categoryId)
    val colors = CategoryColors(cat)
    val hasError = v.status.contains("重复") || v.status.contains("失败")
    Row(Modifier.fillMaxWidth().heightIn(min = 82.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box {
            Surface(shape = RoundedCornerShape(18.dp), color = colors.container) {
                Icon(if (cat == null) Icons.Rounded.ReceiptLong else categoryIcon(cat), cat?.title ?: "未分类", Modifier.padding(12.dp).size(26.dp), tint = colors.content)
            }
            if (hasError) Surface(Modifier.align(Alignment.TopEnd).size(11.dp), shape = CircleShape, color = MaterialTheme.colorScheme.error, border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainer)) {}
        }
        Column(Modifier.weight(1f)) { Text(v.seller.ifBlank { "未识别销方" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(listOf(v.date, v.status,cat?.title).filterNotNull().filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(horizontalAlignment = Alignment.End) { Text(money(v.totalCents), style = tabular(MaterialTheme.typography.titleMedium), fontWeight = FontWeight.SemiBold); Text(v.number.ifBlank { "号码待录入" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun EmptyInvoices(title: String, body: String) { Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(Icons.Rounded.ReceiptLong, null, Modifier.padding(18.dp).size(36.dp)) }; Text(title, style = MaterialTheme.typography.titleLarge); Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun InvoiceDetail(v:InvoiceEntity,onBack:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onRecognize:()->Unit,onReclassify:()->Unit,onCategory:(Category,Boolean)->Unit,recognizing:Boolean,onMessage:(String)->Unit){
 val ctx=LocalContext.current;var menu by remember{mutableStateOf(false)};var confirmDelete by remember{mutableStateOf(false)};var actionBusy by remember{mutableStateOf(false)};var categorySheet by remember{mutableStateOf(false)}
 val open={if(!actionBusy){actionBusy=true;InvoiceFileActions.open(ctx,v){onMessage(it)};actionBusy=false}}
 val share={if(!actionBusy){actionBusy=true;val email=ctx.getSharedPreferences("settings",0).getString("email","").orEmpty();InvoiceFileActions.share(ctx,v,email){onMessage(it)};actionBusy=false}}
 Scaffold(topBar={TopAppBar(title={Text("发票详情")},navigationIcon={IconButton(onBack,Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Icon(Icons.Rounded.ArrowBack,"返回")}},actions={Box{IconButton({menu=true},Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Icon(Icons.Rounded.MoreVert,"更多操作")};DropdownMenu(menu,{menu=false}){DropdownMenuItem({Text(if(recognizing)"识别中…" else "重新识别")},{menu=false;onRecognize()},enabled=!recognizing,leadingIcon={Icon(Icons.Rounded.Refresh,"")});DropdownMenuItem({Text("重新自动分类")},{menu=false;onReclassify()},leadingIcon={Icon(Icons.Rounded.AutoAwesome,"")});DropdownMenuItem({Text("删除发票",color=MaterialTheme.colorScheme.error)},{menu=false;confirmDelete=true},leadingIcon={Icon(Icons.Rounded.Delete,"",tint=MaterialTheme.colorScheme.error)})}}})},bottomBar={DetailActions(onEdit,open,share,!actionBusy)}){pad->
  BoxWithConstraints(Modifier.fillMaxSize().padding(pad)){val wide=maxWidth>=700.dp
   if(wide)Row(Modifier.fillMaxSize().padding(24.dp),horizontalArrangement=Arrangement.spacedBy(24.dp)){InvoiceTicket(v,{categorySheet=true},Modifier.weight(1.35f).fillMaxHeight());SupportPanel(v,onEdit,open,share,!actionBusy,Modifier.weight(.65f))}
   else LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp,12.dp,16.dp,28.dp)){item{InvoiceTicket(v,{categorySheet=true},Modifier.fillMaxWidth())}}
  }
 }
 if(categorySheet)CategorySheet(v,{categorySheet=false}){c,r->categorySheet=false;onCategory(c,r)}
 if(confirmDelete)AlertDialog({confirmDelete=false},icon={Icon(Icons.Rounded.Delete,"删除")},title={Text("删除这张发票？")},text={Text("发票记录和保存在本机的原文件都会被永久删除，此操作无法撤销。")},confirmButton={Button({confirmDelete=false;onDelete()},colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("确认删除")}},dismissButton={TextButton({confirmDelete=false}){Text("取消")}})
}

@Composable private fun InvoiceTicket(v:InvoiceEntity,onCategory:()->Unit,modifier:Modifier=Modifier){ElevatedCard(modifier,shape=RoundedCornerShape(32.dp)){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.Top){Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)){Text("电子发票 · 普通发票",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary);Text(v.itemContent.ifBlank{"发票消费"},style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold);CategoryChip(v,onCategory)};StatusPill(v.status)}
 DashedDivider();PartyFlow(v)
 AmountBlock(v)
 DetailRow("开票日期",v.date.ifBlank{"未录入"});DetailRow("发票号码",v.number.ifBlank{"未录入"},true);if(v.code.isNotBlank())DetailRow("发票代码",v.code,true)
 if(v.itemContent.isNotBlank()){SectionTitle("商品明细");Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surfaceContainer){Column(Modifier.fillMaxWidth().padding(18.dp)){Text(v.itemContent,style=MaterialTheme.typography.titleMedium)}}}
 MoreInformation(v)
 }}}

@Composable private fun CategoryChip(v:InvoiceEntity,onClick:()->Unit){
 val c=Category.fromId(v.categoryId);val colors=CategoryColors(c)
 AssistChip(onClick,{Text(c?.title?:"选择分类")},leadingIcon={Icon(c?.let(::categoryIcon)?:Icons.Rounded.Category,null,Modifier.size(18.dp))},modifier=Modifier.heightIn(min=48.dp),colors=AssistChipDefaults.assistChipColors(containerColor=colors.container,labelColor=colors.content,leadingIconContentColor=colors.content),border=AssistChipDefaults.assistChipBorder(enabled=true,borderColor=colors.accent,borderWidth=1.dp))
}
private fun categorySourceLabel(s:String)=when(s){CategorySource.AUTO.name->"自动";CategorySource.RULE.name->"商家规则";CategorySource.MANUAL.name->"手动";else->"未知"}
private fun categoryIcon(c:Category)=when(c){Category.FOOD->Icons.Rounded.Restaurant;Category.TRANSPORT->Icons.Rounded.DirectionsCar;Category.LODGING->Icons.Rounded.Hotel;Category.COMMUNICATION->Icons.Rounded.Phone;Category.OFFICE->Icons.Rounded.Inventory2;Category.SOFTWARE->Icons.Rounded.Cloud;Category.LIFE->Icons.Rounded.HomeRepairService;Category.MEDICAL->Icons.Rounded.MedicalServices;Category.EDUCATION->Icons.Rounded.School;Category.VEHICLE->Icons.Rounded.LocalGasStation;Category.DIGITAL->Icons.Rounded.Devices;Category.OTHER->Icons.Rounded.Category}
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CategorySheet(v:InvoiceEntity,onDismiss:()->Unit,onChoose:(Category,Boolean)->Unit){var rememberSeller by rememberSaveable{mutableStateOf(false)};ModalBottomSheet(onDismissRequest=onDismiss){Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)){Text("消费分类",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold);Text("分类仅保存在设备和完整备份中",color=MaterialTheme.colorScheme.onSurfaceVariant);LazyVerticalGrid(columns=androidx.compose.foundation.lazy.grid.GridCells.Adaptive(132.dp),modifier=Modifier.heightIn(max=420.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){items(Category.entries.size){i->val c=Category.entries[i];val colors=CategoryColors(c);val selected=v.categoryId==c.id;Surface(onClick={onChoose(c,rememberSeller)},shape=RoundedCornerShape(18.dp),color=colors.container,contentColor=colors.content,border=androidx.compose.foundation.BorderStroke(if(selected) 2.dp else 1.dp,if(selected) colors.accent else colors.accent.copy(alpha=.35f)),modifier=Modifier.heightIn(min=64.dp)){Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically){Surface(shape=RoundedCornerShape(12.dp),color=colors.accent.copy(alpha=.16f)){Icon(categoryIcon(c),null,Modifier.padding(8.dp).size(22.dp),tint=colors.content)};Spacer(Modifier.width(8.dp));Text(c.title,Modifier.weight(1f));if(selected)Icon(Icons.Rounded.Check,"当前分类",tint=colors.accent)}}}};Row(Modifier.fillMaxWidth().heightIn(min=56.dp).clickable(enabled=v.seller.isNotBlank()){if(v.seller.isNotBlank())rememberSeller=!rememberSeller}.padding(vertical=4.dp),verticalAlignment=Alignment.CenterVertically){Checkbox(rememberSeller,{rememberSeller=it},enabled=v.seller.isNotBlank());Column{Text("以后将此销售方自动归为所选分类");if(v.seller.isBlank())Text("销售方为空，无法创建规则",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)else Text(v.seller,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}};Spacer(Modifier.height(16.dp))}}}

@Composable private fun RecognitionDiagnostics(v:InvoiceEntity){var expanded by remember(v.id){mutableStateOf(false)};ElevatedCard(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("识别诊断",style=MaterialTheme.typography.titleMedium);Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)};if(expanded){val missing=buildList{if(v.number.isBlank())add("发票号码");if(v.date.isBlank())add("开票日期");if(v.totalCents==0L)add("价税合计")};Text("缺少字段：${missing.joinToString().ifBlank{"无"}}");if(v.lastError.isNotBlank())Text("最近错误：${v.lastError}",color=MaterialTheme.colorScheme.error);Text("识别原文（仅本地显示）：\n${v.rawText.take(1200).ifBlank{"暂无识别文字"}}",style=MaterialTheme.typography.bodySmall)}}}}
@Composable private fun Party(label:String,name:String,sub:String){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){SectionTitle(label);Text(name.ifBlank{"未识别"},style=MaterialTheme.typography.titleMedium);if(sub.isNotBlank())Text(sub,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable private fun PartyFlow(v:InvoiceEntity){BoxWithConstraints(Modifier.fillMaxWidth()){if(maxWidth<430.dp)Column(verticalArrangement=Arrangement.spacedBy(14.dp)){Party("购方",v.buyer,v.buyerTaxId);Icon(Icons.Rounded.ArrowDownward,"至",tint=MaterialTheme.colorScheme.primary);Party("销方",v.seller,v.sellerTaxId)}else Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Box(Modifier.weight(1f)){Party("购方",v.buyer,v.buyerTaxId)};Icon(Icons.Rounded.ArrowForward,"至",tint=MaterialTheme.colorScheme.primary);Box(Modifier.weight(1f)){Party("销方",v.seller,v.sellerTaxId)}}}}
@Composable private fun MoreInformation(v:InvoiceEntity){var expanded by remember(v.id){mutableStateOf(false)};OutlinedCard(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("更多信息 / 识别诊断",style=MaterialTheme.typography.titleMedium);Icon(if(expanded)Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,null)};if(expanded){DetailRow("添加日期",DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(v.createdAt)));DetailRow("来源",v.source.ifBlank{"未知"});if(v.sha256.isNotBlank())DetailRow("文件指纹",v.sha256,true);DetailRow("分类来源",categorySourceLabel(v.categorySource));DetailRow("分类置信度","${v.categoryConfidence}%");if(v.categoryReason.isNotBlank())DetailRow("分类理由",v.categoryReason);RecognitionDiagnostics(v)}}}}
@Composable private fun AmountBlock(v:InvoiceEntity){Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(24.dp)){Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("价税合计",style=MaterialTheme.typography.labelLarge);Text(money(v.totalCents),style=tabular(MaterialTheme.typography.headlineLarge),fontWeight=FontWeight.Bold,modifier=Modifier.fillMaxWidth());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)){AmountItem("不含税金额",v.amountCents,Modifier.weight(1f));AmountItem("税额",v.taxCents,Modifier.weight(1f))}}}}
@Composable private fun AmountItem(k:String,v:Long,m:Modifier){Column(m){Text(k,style=MaterialTheme.typography.labelMedium);Text(money(v),style=tabular(MaterialTheme.typography.titleMedium),maxLines=2,overflow=TextOverflow.Visible)}}
@Composable private fun DetailRow(k:String,v:String,mono:Boolean=false){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.Top){Text(k,Modifier.width(88.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(v,Modifier.weight(1f),style=if(mono)tabular(MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge)}}
@Composable private fun SectionTitle(s:String){Text(s,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
@Composable private fun StatusPill(s:String){Surface(shape=CircleShape,color=if(s.contains("重复")||s.contains("失败"))MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer){Text(s,Modifier.padding(horizontal=12.dp,vertical=7.dp),style=MaterialTheme.typography.labelMedium)}}
@Composable private fun DashedDivider(){val c=MaterialTheme.colorScheme.outlineVariant;Canvas(Modifier.fillMaxWidth().height(1.dp)){drawLine(c,androidx.compose.ui.geometry.Offset.Zero,androidx.compose.ui.geometry.Offset(size.width,0f),strokeWidth=1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(10f,8f)))}}

@Composable private fun DetailActions(onEdit:()->Unit,onOpen:()->Unit,onShare:()->Unit,enabled:Boolean){BottomAppBar(contentPadding=PaddingValues(horizontal=10.dp),actions={TextButton(onEdit,enabled=enabled){Icon(Icons.Rounded.Edit,"编辑");Text("编辑")};TextButton(onOpen,enabled=enabled){Icon(Icons.Rounded.OpenInNew,"原件");Text("原件")};Spacer(Modifier.weight(1f));Button(onShare,enabled=enabled){Icon(Icons.Rounded.Share,"分享当前原件");Spacer(Modifier.width(6.dp));Text("分享 / 导出")}})}
@Composable private fun SupportPanel(v:InvoiceEntity,onEdit:()->Unit,onOpen:()->Unit,onShare:()->Unit,enabled:Boolean,m:Modifier){ElevatedCard(m){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){Text("原件与操作",style=MaterialTheme.typography.headlineSmall);Text(v.source.ifBlank{"未记录文件名"},style=MaterialTheme.typography.bodyLarge);Text(v.mimeType.ifBlank{"未知文件类型"},color=MaterialTheme.colorScheme.onSurfaceVariant);OutlinedButton(onEdit,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Rounded.Edit,"");Spacer(Modifier.width(8.dp));Text("编辑信息")};OutlinedButton(onOpen,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Rounded.OpenInNew,"");Spacer(Modifier.width(8.dp));Text("预览原件")};Button(onShare,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Icons.Rounded.Share,"");Spacer(Modifier.width(8.dp));Text("分享当前原件")};Text("“导出全部”位于设置页",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}

private fun tabular(style:TextStyle)=style.copy(fontFeatureSettings="tnum")
private fun invoiceType(v:InvoiceEntity)=when{v.mimeType.contains("xml")->"数电发票";v.mimeType.contains("pdf")->"电子发票";v.mimeType.startsWith("image/")->"发票影像";else->"发票"}
private fun duplicateLabel(s:String)=if(s.contains("重复"))s else "未发现重复"
private fun productSummary(v:InvoiceEntity):String {val raw=v.originalValues;return Regex("(?:item|goods|content)=([^,}]+)",RegexOption.IGNORE_CASE).find(raw)?.groupValues?.getOrNull(1)?.trim().orEmpty().ifBlank{"商品明细请查看原件"}}
@Composable
fun EditDialog(v:InvoiceEntity,onDismiss:()->Unit,onSave:(InvoiceEntity)->Unit,onDelete:(InvoiceEntity)->Unit) {
 var number by remember(v){mutableStateOf(v.number)}
 var code by remember(v){mutableStateOf(v.code)}
 var date by remember(v){mutableStateOf(v.date)}
 var seller by remember(v){mutableStateOf(v.seller)}
 var taxId by remember(v){mutableStateOf(v.sellerTaxId)}
 var amount by remember(v){mutableStateOf("%.2f".format(v.amountCents/100.0))}
 var tax by remember(v){mutableStateOf("%.2f".format(v.taxCents/100.0))}
 var total by remember(v){mutableStateOf("%.2f".format(v.totalCents/100.0))}
 AlertDialog(
  onDismissRequest=onDismiss,
  confirmButton={Button({onSave(v.copy(number=number,code=code,date=date,seller=seller,sellerTaxId=taxId,amountCents=InvoiceLogic.money(amount),taxCents=InvoiceLogic.money(tax),totalCents=InvoiceLogic.money(total)))}){Text("保存")}},
  dismissButton={TextButton(onDismiss){Text("取消")}}, title={Text("核对发票")},
  text={LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
   item{Text("原始提取：${v.originalValues}",style=MaterialTheme.typography.labelSmall)}
   items(listOf("发票代码" to (code to {x:String->code=x}),"发票号码" to(number to{x:String->number=x}),"开票日期" to(date to{x:String->date=x}),"销方名称" to(seller to{x:String->seller=x}),"销方税号" to(taxId to{x:String->taxId=x}),"不含税金额" to(amount to{x:String->amount=x}),"税额" to(tax to{x:String->tax=x}),"价税合计" to(total to{x:String->total=x}))){x->OutlinedTextField(x.second.first,x.second.second,label={Text(x.first)},singleLine=true)}
  }}
 )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AboutScreen(onBack:()->Unit,onMessage:(String)->Unit){
 val ctx=LocalContext.current
 val debug=BuildConfig.DEBUG
 Scaffold(topBar={TopAppBar(title={Text("关于发票匣")},navigationIcon={IconButton(onBack,Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)){Icon(Icons.Rounded.ArrowBack,"返回")}})}){pad->
  LazyColumn(Modifier.fillMaxSize().padding(pad),contentPadding=PaddingValues(20.dp,18.dp,20.dp,40.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
   item{ElevatedCard(shape=RoundedCornerShape(32.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer){Icon(Icons.Rounded.ReceiptLong,"发票匣",Modifier.padding(18.dp).size(40.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)};Text("发票匣",style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold);Text("本地优先的个人发票管理工具",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){AssistChip({},label={Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")});if(debug)AssistChip({},label={Text("调试版")})}}}}
   item{AboutCard("产品能力",Icons.Rounded.AutoAwesome,listOf("本地导入与离线识别 PDF、XML、JPG、PNG","票额、税额统计与重复发票检测","原件预览、分享及完整资料导出","用户主动触发的 WebDAV 备份与恢复"))}
   item{AboutCard("隐私与数据",Icons.Rounded.Security,listOf("发票和结构化数据默认只保存在设备本地","仅在你主动配置并操作 WebDAV 时联网","WebDAV 凭据由 Android Keystore 加密保护","未集成广告或用户行为分析 SDK"))}
   item{AboutCard("支持范围",Icons.Rounded.Description,listOf("支持 PDF、XML、JPG、PNG；扫描件使用端侧 OCR","暂不支持 OFD、官方发票验真","暂不支持微信或支付宝票夹自动同步"))}
   item{AboutCard("开源许可",Icons.Rounded.Code,listOf("AndroidX、Jetpack Compose、Room — Apache License 2.0","OkHttp、Gson、PDFBox Android — Apache License 2.0","ML Kit Text Recognition — Google APIs Terms / Android Software Development Kit License"))}
   item{ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text("构建信息",style=MaterialTheme.typography.titleLarge);Text("包名  ${BuildConfig.APPLICATION_ID}");Text("目标平台  Android API 37");Text("版本  ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})");FilledTonalButton(onClick={val info="发票匣 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n${BuildConfig.APPLICATION_ID}\nAndroid ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})";val cm=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;cm.setPrimaryClip(android.content.ClipData.newPlainText("应用信息",info));onMessage("应用信息已复制")},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Icon(Icons.Rounded.ContentCopy,null);Spacer(Modifier.width(8.dp));Text("复制应用信息")}}}}
  }
 }
}
@Composable private fun AboutCard(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,lines:List<String>){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Text(title,style=MaterialTheme.typography.titleLarge)};lines.forEach{Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Top){Text("•",color=MaterialTheme.colorScheme.primary);Text(it,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)}}}}}


