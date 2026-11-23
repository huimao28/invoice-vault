package com.minis.invoicevault

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.sqrt

data class RecognizedDocument(val text:String,val source:String)

object DocumentRecognizer {
 private const val MAX_PIXELS=16_000_000L
 private const val PDF_PAGES=3
 suspend fun recognizeImage(file:File):String=withContext(Dispatchers.IO){
  val bitmap=decodeSampled(file);try { recognize(bitmap) } finally { bitmap.recycle() }
 }
 /** Native text is both more accurate and cheaper. Raster OCR is used only when no useful text layer exists. */
 suspend fun recognizePdf(context:Context,file:File):RecognizedDocument=withContext(Dispatchers.IO){
  require(file.inputStream().use{val h=ByteArray(5);it.read(h)==5&&h.decodeToString()=="%PDF-"}){"PDF签名无效"}
  PDFBoxResourceLoader.init(context.applicationContext)
  val native=runCatching { PDDocument.load(file).use { document ->
   require(document.numberOfPages>0){"PDF没有页面"}
   PDFTextStripper().apply { sortByPosition=true;startPage=1;endPage=minOf(document.numberOfPages,PDF_PAGES) }.getText(document).trim()
  }}.getOrDefault("")
  if(isUsefulTextLayer(native)) return@withContext RecognizedDocument(native,"PDF文本层")
  RecognizedDocument(recognizePdfPages(file),"OCR回退")
 }
 internal fun isUsefulTextLayer(text:String):Boolean {
  val visible=text.count{!it.isWhitespace()&&!it.isISOControl()}
  return visible>=30 && (text.contains("发票")||text.contains("购买方")||text.contains("销售方")||text.contains("纳税人识别号"))
 }
 private suspend fun recognizePdfPages(file:File):String {
  val result=StringBuilder()
  ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
   require(renderer.pageCount>0){"PDF没有页面"}
   repeat(minOf(renderer.pageCount,PDF_PAGES)){index->renderer.openPage(index).use { page ->
    var w=ceil(page.width*200.0/72.0).toInt().coerceAtLeast(1);var h=ceil(page.height*200.0/72.0).toInt().coerceAtLeast(1)
    val pixels=w.toLong()*h;if(pixels>MAX_PIXELS){val scale=sqrt(MAX_PIXELS.toDouble()/pixels);w=(w*scale).toInt();h=(h*scale).toInt()}
    val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    try { bitmap.eraseColor(Color.WHITE);page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);result.append("\n--- 第${index+1}页 ---\n").append(recognize(bitmap)) } finally {bitmap.recycle()}
   }}
  }}
  return result.toString().trim()
 }
 private fun decodeSampled(file:File):Bitmap {
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds);require(bounds.outWidth>0&&bounds.outHeight>0){"图片无法解码"}
  var sample=1;while(bounds.outWidth.toLong()/sample*(bounds.outHeight/sample)>MAX_PIXELS)sample*=2
  val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888})?:error("图片无法解码")
  val orientation=runCatching{ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
  val matrix=Matrix().apply{when(orientation){ExifInterface.ORIENTATION_FLIP_HORIZONTAL->setScale(-1f,1f);ExifInterface.ORIENTATION_ROTATE_180->setRotate(180f);ExifInterface.ORIENTATION_FLIP_VERTICAL->setScale(1f,-1f);ExifInterface.ORIENTATION_TRANSPOSE->{setRotate(90f);postScale(-1f,1f)};ExifInterface.ORIENTATION_ROTATE_90->setRotate(90f);ExifInterface.ORIENTATION_TRANSVERSE->{setRotate(-90f);postScale(-1f,1f)};ExifInterface.ORIENTATION_ROTATE_270->setRotate(-90f)}}
  if(orientation==ExifInterface.ORIENTATION_NORMAL||orientation==ExifInterface.ORIENTATION_UNDEFINED)return decoded
  return try{Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,matrix,true).also{if(it!==decoded)decoded.recycle()}}catch(e:Exception){decoded}
 }
 private suspend fun recognize(bitmap:Bitmap):String {val recognizer=TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build());return try { recognizer.process(InputImage.fromBitmap(bitmap,0)).await().text } finally { recognizer.close() }}
 private suspend fun <T> Task<T>.await():T=suspendCancellableCoroutine{c->addOnSuccessListener{if(c.isActive)c.resume(it)}.addOnFailureListener{if(c.isActive)c.resumeWithException(it)}.addOnCanceledListener{c.cancel()}}
}
