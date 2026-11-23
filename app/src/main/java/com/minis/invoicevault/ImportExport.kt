package com.minis.invoicevault

import android.content.*
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VaultRepository(private val context:Context,private val dao:InvoiceDao){
 companion object{const val MAX_BYTES=50L*1024*1024;val TYPES=setOf("application/pdf","application/xml","text/xml","image/jpeg","image/png")}
 data class Result(val name:String,val outcome:String,val invoice:InvoiceEntity?=null)
 suspend fun reRecognize(id:Long):Result=withContext(Dispatchers.IO){
  val old=dao.byId(id)?:error("发票记录不存在")
  val file=File(File(context.filesDir,"originals"),old.storedName);require(file.isFile){"原文件不存在：${old.source}"}
  try{val updated=recognizeInto(old,file);dao.update(updated);Result(old.source,summary("已重新识别已有发票",updated),updated)}catch(e:Exception){
   val message=e.message?.take(300)?:e.javaClass.simpleName;dao.update(old.copy(status="待确认·识别失败",lastError=message,recognizedAt=System.currentTimeMillis()));throw IllegalStateException("重新识别失败：$message",e)
  }
 }
 suspend fun import(uri:Uri):Result=withContext(Dispatchers.IO){
  require(uri.scheme==ContentResolver.SCHEME_CONTENT){"仅接受 content URI"};val cr=context.contentResolver;val mime=cr.getType(uri)?.lowercase()?.substringBefore(';')?:"";require(mime in TYPES){"不支持的 MIME: $mime"}
  var display="document";var size=-1L;cr.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null)?.use{c->if(c.moveToFirst()){display=c.getString(0)?:display;size=if(c.isNull(1))-1 else c.getLong(1)}};require(size<0||size<=MAX_BYTES){"文件超过 50MB"}
  val dir=File(context.filesDir,"originals").apply{mkdirs()};val tmp=File.createTempFile("incoming-",".part",dir);var dst:File?=null
  try{val digest=MessageDigest.getInstance("SHA-256");var count=0L;cr.openInputStream(uri)?.use{i->tmp.outputStream().use{o->val b=ByteArray(32768);while(true){val n=i.read(b);if(n<0)break;count+=n;require(count<=MAX_BYTES){"文件超过 50MB"};digest.update(b,0,n);o.write(b,0,n)}}}?:error("无法读取文件")
   val hash=digest.digest().joinToString(""){"%02x".format(it)};val existing=dao.byHash(hash)
   if(existing!=null){tmp.delete();return@withContext if(RecognitionPolicy.shouldReRecognize(existing)){val r=reRecognize(existing.id);r.copy(name=display)}else Result(display,"已跳过：文件重复",existing)}
   val safe=display.replace(Regex("[^A-Za-z0-9._-]"),"_").takeLast(100);val stored="${System.currentTimeMillis()}-${hash.take(12)}-$safe";dst=File(dir,stored);check(tmp.renameTo(dst))
   val base=InvoiceEntity(source=display,sha256=hash,storedName=stored,mimeType=mime);val recognized=try{recognizeInto(base,dst)}catch(e:Exception){val msg=e.message?.take(300)?:e.javaClass.simpleName;InvoiceEntity(source=display,sha256=hash,storedName=stored,mimeType=mime,status="待确认·识别失败",lastError=msg,recognizedAt=System.currentTimeMillis())}
   val id=dao.insert(recognized);val saved=recognized.copy(id=id);Result(display,summary(if(recognized.lastError.isBlank())"已导入" else "已导入但识别失败",saved),saved)
  }catch(e:Exception){tmp.delete();dst?.delete();throw e}
 }
 private suspend fun recognizeInto(old:InvoiceEntity,file:File):InvoiceEntity{
  val fields=when(old.mimeType){"application/xml","text/xml"->XmlInvoiceParser.parse(file.inputStream());"application/pdf"->{val recognized=DocumentRecognizer.recognizePdf(context,file);InvoiceFieldParser.parse(recognized.text).asMap().toMutableMap().apply{this["raw"]="【识别来源：${recognized.source}】\n${recognized.text}"}};else->{val raw=DocumentRecognizer.recognizeImage(file);InvoiceFieldParser.parse(raw).asMap()}}
  val raw=fields["raw"].orEmpty();val missing=buildList{if(fields["number"].isNullOrBlank())add("发票号码");if(fields["date"].isNullOrBlank())add("开票日期");if(fields["total"].isNullOrBlank())add("价税合计")};val key=InvoiceLogic.identity(fields["code"].orEmpty(),fields["number"].orEmpty(),fields["taxid"].orEmpty())
  var status=RecognitionPolicy.status(raw,missing);val draft=old.copy(number=fields["number"].orEmpty(),code=fields["code"].orEmpty(),date=fields["date"].orEmpty(),buyer=fields["buyer"].orEmpty(),buyerTaxId=fields["buyerTaxId"].orEmpty(),seller=fields["seller"].orEmpty(),sellerTaxId=fields["taxid"].orEmpty(),itemContent=fields["item"].orEmpty(),amountCents=InvoiceLogic.money(fields["amount"].orEmpty()),taxCents=InvoiceLogic.money(fields["tax"].orEmpty()),totalCents=InvoiceLogic.money(fields["total"].orEmpty()),identityKey=key,status=status,originalValues=fields.filterKeys{it!="raw"}.entries.joinToString(prefix="{",postfix="}"){"${it.key}=${it.value}"},rawText=raw,lastError="",recognizedAt=System.currentTimeMillis())
  if((key.isNotBlank()&&dao.byIdentity(key,old.id)!=null)||dao.allNow().any{it.id!=old.id&&InvoiceLogic.similar(it,draft)})status="疑似重复·$status"
  val rule=CategoryEngine.normalizeSeller(draft.seller).takeIf{it.isNotBlank()}?.let{dao.rule(it)}
  val decision=CategoryEngine.classify(draft.itemContent,draft.seller,draft.rawText,rule)
  return CategoryEngine.applyUnlessManual(draft.copy(status=status),decision)
 }
 suspend fun autoClassify(v:InvoiceEntity,force:Boolean=false):InvoiceEntity=withContext(Dispatchers.IO){val rule=CategoryEngine.normalizeSeller(v.seller).takeIf{it.isNotBlank()}?.let{dao.rule(it)};val result=CategoryEngine.applyUnlessManual(v,CategoryEngine.classify(v.itemContent,v.seller,v.rawText,rule),force);dao.update(result);result}
 suspend fun setCategory(v:InvoiceEntity,category:Category,rememberSeller:Boolean):InvoiceEntity=withContext(Dispatchers.IO){if(rememberSeller){val key=CategoryEngine.normalizeSeller(v.seller);require(key.isNotBlank()){"销售方为空，无法创建规则"};dao.upsertRule(MerchantCategoryRule(key,v.seller,category.id))};v.copy(categoryId=category.id,categorySource=CategorySource.MANUAL.name,categoryConfidence=100,categoryReason="用户手动选择").also{dao.update(it)}}
 private fun summary(prefix:String,v:InvoiceEntity):String{val s=listOfNotNull(v.number.takeIf{it.isNotBlank()}?.let{"号码 $it"},v.seller.takeIf{it.isNotBlank()},v.totalCents.takeIf{it>0}?.let{money(it)}).joinToString(" · ");return "$prefix：${v.status}${if(s.isBlank())"" else " · $s"}"}
 fun originalExists(v:InvoiceEntity)=File(File(context.filesDir,"originals"),v.storedName).isFile
 fun deleteFile(v:InvoiceEntity){File(File(context.filesDir,"originals"),v.storedName).delete()}
 suspend fun exportTo(output:OutputStream)=withContext(Dispatchers.IO){val rows=dao.allNow();ZipOutputStream(output).use{z->z.putNextEntry(ZipEntry("invoices.csv"));z.write(csv(rows).toByteArray());z.closeEntry();rows.forEach{v->val f=File(File(context.filesDir,"originals"),v.storedName);if(f.isFile){z.putNextEntry(ZipEntry("originals/${v.storedName}"));f.inputStream().use{it.copyTo(z)};z.closeEntry()}}}}
 fun csv(rows:List<InvoiceEntity>)=buildString{append("号码,代码,日期,购方,销方,销方税号,不含税金额,税额,价税合计,分类名称,分类来源,置信度,来源,SHA256,状态\r\n");rows.forEach{v->append(listOf(v.number,v.code,v.date,v.buyer,v.seller,v.sellerTaxId,"%.2f".format(v.amountCents/100.0),"%.2f".format(v.taxCents/100.0),"%.2f".format(v.totalCents/100.0),Category.fromId(v.categoryId)?.title.orEmpty(),categorySourceTitle(v.categorySource),v.categoryConfidence.toString(),v.source,v.sha256,v.status).joinToString(",",transform=InvoiceLogic::csv)).append("\r\n")}}
 private fun categorySourceTitle(s:String)=when(s){CategorySource.AUTO.name->"自动";CategorySource.RULE.name->"商家规则";CategorySource.MANUAL.name->"手动";else->"未知"}
 fun shareExport(file:File,email:String):Intent{val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file);return Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM,uri).putExtra(Intent.EXTRA_EMAIL,arrayOf(email).filter{it.isNotBlank()}.toTypedArray()).putExtra(Intent.EXTRA_SUBJECT,"发票匣导出").putExtra(Intent.EXTRA_TEXT,"附件为发票数据 CSV 与原始文件归档，请查收。").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newRawUri("invoice-export",uri)}}
}
