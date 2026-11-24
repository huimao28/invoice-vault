package com.huimaotech.invoicevault

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File

internal const val FILE_PROVIDER_SUFFIX = ".files"
internal const val READ_URI_FLAG = Intent.FLAG_GRANT_READ_URI_PERMISSION

data class ShareIntentPlan(val action:String,val mimeType:String,val includeStream:Boolean=true,val readGrant:Boolean=true,val clipData:Boolean=true,val emails:Array<String> = emptyArray())

internal object InvoiceFileActions {
 fun originalFile(context:Context, invoice:InvoiceEntity):File? {
  if (invoice.storedName.isBlank()) return null
  val root=File(context.filesDir,"originals")
  val candidate=File(root,invoice.storedName)
  return runCatching { if(candidate.canonicalFile.parentFile==root.canonicalFile) candidate else null }.getOrNull()
 }
 fun mime(invoice:InvoiceEntity,file:File):String {
  val declared=invoice.mimeType.substringBefore(';').trim().lowercase()
  if(declared.contains('/') && declared!="application/octet-stream") return declared
  return MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
 }
 fun plan(invoice:InvoiceEntity,file:File,action:String,email:String="")=ShareIntentPlan(action,mime(invoice,file),emails=if(email.isBlank()) emptyArray() else arrayOf(email))
 fun build(context:Context,file:File,plan:ShareIntentPlan):Intent {
  val uri=FileProvider.getUriForFile(context,context.packageName+FILE_PROVIDER_SUFFIX,file)
  return Intent(plan.action).apply {
   type=plan.mimeType
   if(plan.action==Intent.ACTION_VIEW) data=uri
   if(plan.includeStream) putExtra(Intent.EXTRA_STREAM,uri)
   if(plan.emails.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL,plan.emails)
   if(plan.readGrant) addFlags(READ_URI_FLAG)
   if(plan.clipData) clipData=ClipData.newRawUri("invoice",uri)
  }
 }
 fun hasHandler(context:Context,intent:Intent):Boolean =
  context.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty() || intent.resolveActivity(context.packageManager)!=null

 fun open(context:Context,invoice:InvoiceEntity,onResult:(String)->Unit) {
  val file=originalFile(context,invoice)
  if(file==null || !file.isFile){onResult("原件不存在，可能已被移动或删除");return}
  try {
   val view=build(context,file,plan(invoice,file,Intent.ACTION_VIEW))
   if(hasHandler(context,view)) launch(context,Intent.createChooser(view,"预览发票原件"))
   else {
    val send=build(context,file,plan(invoice,file,Intent.ACTION_SEND))
    if(hasHandler(context,send)) {onResult("没有可预览此文件的应用，已改用系统分享");launch(context,Intent.createChooser(send,"使用其他应用打开"))}
    else onResult("没有可预览或接收此文件的应用")
   }
  } catch(e:ActivityNotFoundException){onResult("没有可打开原件的应用")}
    catch(e:SecurityException){onResult("无法授予原件读取权限：${e.message.orEmpty()}")}
    catch(e:IllegalArgumentException){onResult("原件路径不在安全共享目录中")}
 }
 fun share(context:Context,invoice:InvoiceEntity,email:String="",onResult:(String)->Unit) {
  val file=originalFile(context,invoice)
  if(file==null || !file.isFile){onResult("原件不存在，无法分享");return}
  try {
   val send=build(context,file,plan(invoice,file,Intent.ACTION_SEND,email))
   if(!hasHandler(context,send)){onResult("没有可接收此文件的应用");return}
   launch(context,Intent.createChooser(send,"分享当前原件"))
  } catch(e:ActivityNotFoundException){onResult("没有可分享原件的应用")}
    catch(e:SecurityException){onResult("无法授予原件读取权限：${e.message.orEmpty()}")}
    catch(e:IllegalArgumentException){onResult("原件路径不在安全共享目录中")}
 }
 private fun launch(context:Context,intent:Intent){if(context !is Activity)intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(intent)}
}
