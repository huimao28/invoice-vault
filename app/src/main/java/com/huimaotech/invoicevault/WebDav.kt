package com.huimaotech.invoicevault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import okhttp3.*
import okhttp3.Credentials
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.File
import java.net.*
import java.security.KeyStore
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.net.ssl.SSLException
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

data class WebDavConfig(val serverUrl:String="",val username:String="",val password:String="",val remoteDir:String="/InvoiceVault/",val allowHttp:Boolean=false)
data class RemoteBackup(val name:String,val href:String,val size:Long,val modified:String="")

object WebDavRules {
 fun normalizeUrl(raw:String,allowHttp:Boolean=false):String { val u=URI(raw.trim()); require(u.scheme.equals("https",true)||u.scheme.equals("http",true)){"仅支持 HTTPS URL"};require(u.host!=null&&u.userInfo==null&&u.fragment==null){"服务器 URL 无效"};if(u.scheme.equals("http",true)) throw IllegalArgumentException(if(allowHttp)"系统安全策略仍禁止明文 HTTP；请为局域网服务配置 HTTPS" else "仅允许 HTTPS；明文 HTTP 会泄露凭据");return URI("https",null,u.host.lowercase(),u.port,u.path.ifBlank{"/"},u.query,null).toString().trimEnd('/')
 }
 fun normalizePath(raw:String):String {val decoded=runCatching{URLDecoder.decode(raw.trim(),"UTF-8")}.getOrElse{throw IllegalArgumentException("远端目录编码无效")};require(!decoded.contains('\\')&&!decoded.split('/').any{it==".."||it=="."}){"远端目录不能包含路径穿越"};val parts=decoded.split('/').filter{it.isNotBlank()};require(parts.isNotEmpty()){"远端目录不能为空"};return "/"+parts.joinToString("/"){URLEncoder.encode(it,"UTF-8").replace("+","%20") }+"/"}
}

object AesGcmCodec {fun encrypt(key:SecretKey,plain:ByteArray):ByteArray{val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key)};return c.iv+c.doFinal(plain)};fun decrypt(key:SecretKey,data:ByteArray):ByteArray{require(data.size>28);val c=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,data.copyOfRange(0,12)))};return c.doFinal(data.copyOfRange(12,data.size))}}

class CredentialStore(private val context:Context){private val prefs=context.getSharedPreferences("webdav_secure",Context.MODE_PRIVATE);private val gson=Gson();private val alias="invoice_vault_webdav_aes"
 private fun key():SecretKey{val ks=KeyStore.getInstance("AndroidKeyStore").apply{load(null)};(ks.getKey(alias,null) as? SecretKey)?.let{return it};return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").run{init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());generateKey()}}
 fun save(c:WebDavConfig){val data=AesGcmCodec.encrypt(key(),gson.toJson(c).toByteArray());prefs.edit().putString("blob",android.util.Base64.encodeToString(data,android.util.Base64.NO_WRAP)).apply()}
 fun load():WebDavConfig?=runCatching{val all=android.util.Base64.decode(prefs.getString("blob",null),android.util.Base64.NO_WRAP);gson.fromJson(String(AesGcmCodec.decrypt(key(),all)),WebDavConfig::class.java)}.getOrNull()
 fun clear(){prefs.edit().clear().apply();runCatching{KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry(alias)}}}
}

class WebDavClient(config:WebDavConfig){private val base=WebDavRules.normalizeUrl(config.serverUrl,config.allowHttp);val dir=WebDavRules.normalizePath(config.remoteDir);private val auth=Credentials.basic(config.username,config.password,Charsets.UTF_8);private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(60,TimeUnit.SECONDS).writeTimeout(120,TimeUnit.SECONDS).followRedirects(false).build()
 private fun url(path:String)=base+path
 private fun request(method:String,path:String,body:RequestBody?=null,vararg headers:Pair<String,String>)=Request.Builder().url(url(path)).header("Authorization",auth).apply{headers.forEach{header(it.first,it.second)}}.method(method,body).build()
 private fun execute(r:Request):Response=try{client.newCall(r).execute()}catch(e:Exception){throw WebDavException.safe(e)}
 fun ensureDirectory(){val parts=dir.trim('/').split('/');var p="/";for(part in parts){p+=part+"/";execute(request("PROPFIND",p,ByteArray(0).toRequestBody(null),"Depth" to "0")).use{r->when(r.code){200,207->{};404->execute(request("MKCOL",p,ByteArray(0).toRequestBody(null))).use{m->if(m.code !in listOf(201,405))fail(m.code)};else->fail(r.code)}}}}
 fun list():List<RemoteBackup>{ensureDirectory();execute(request("PROPFIND",dir,ByteArray(0).toRequestBody(null),"Depth" to "1")).use{r->if(r.code !in listOf(200,207))fail(r.code);return parseMultiStatus(r.body?.byteStream()?:return emptyList()).filter{it.name.endsWith(".zip",true)}}}
 fun upload(file:File,progress:(Long,Long)->Unit={_,_->}) {ensureDirectory();val part=".${file.name}.${System.currentTimeMillis()}.tmp";put(part,file,progress,false);execute(request("MOVE",dir+part,null,"Destination" to url(dir+file.name),"Overwrite" to "F")).use{r->if(r.code !in listOf(201,204)){val fallback=file.name.removeSuffix(".zip")+"-fallback-${System.currentTimeMillis()}.zip";put(fallback,file,progress,true);runCatching{delete(part)}}}}
 private fun put(name:String,file:File,progress:(Long,Long)->Unit,createOnly:Boolean){val body=object:RequestBody(){override fun contentType()="application/zip".toMediaType();override fun contentLength()=file.length();override fun writeTo(s:okio.BufferedSink){file.inputStream().use{i->val b=ByteArray(65536);var done=0L;while(true){val n=i.read(b);if(n<0)break;s.write(b,0,n);done+=n;progress(done,file.length())}}}};val req=if(createOnly)request("PUT",dir+name,body,"If-None-Match" to "*") else request("PUT",dir+name,body);execute(req).use{if(it.code !in listOf(200,201,204))fail(it.code)}}
 fun download(item:RemoteBackup,to:File,progress:(Long,Long)->Unit={_,_->}){require(item.name.endsWith(".zip",true));execute(request("GET",dir+encodeName(item.name))).use{r->if(!r.isSuccessful)fail(r.code);val total=r.body?.contentLength()?:-1;require(total<0||total<=SecureBackup.MAX_TOTAL){"远端备份超过 500MB 限制"};to.outputStream().use{o->r.body!!.byteStream().use{i->val b=ByteArray(65536);var d=0L;while(true){val n=i.read(b);if(n<0)break;d+=n;require(d<=SecureBackup.MAX_TOTAL){"下载超过 500MB 限制"};o.write(b,0,n);progress(d,total)}}}}}
 fun delete(name:String){execute(request("DELETE",dir+encodeName(name))).use{if(it.code !in listOf(200,204,404))fail(it.code)}}
 private fun encodeName(n:String):String {require(!n.contains('/')&&!n.contains(".."));return URLEncoder.encode(n,"UTF-8").replace("+","%20")}
 private fun fail(code:Int):Nothing=throw WebDavException(when(code){401,403->"认证失败，请检查用户名或应用专用密码";404->"远端路径不存在";else->"WebDAV 请求失败（HTTP $code）"})
 companion object{fun parseMultiStatus(input:java.io.InputStream):List<RemoteBackup>{val f=DocumentBuilderFactory.newInstance().apply{isNamespaceAware=true;setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);setFeature("http://xml.org/sax/features/external-general-entities",false)};val nodes=f.newDocumentBuilder().parse(input).getElementsByTagNameNS("DAV:","response");return (0 until nodes.length).mapNotNull{i->val e=nodes.item(i) as Element;val href=e.getElementsByTagNameNS("DAV:","href").item(0)?.textContent?:return@mapNotNull null;val name=URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'),"UTF-8");if(name.isBlank())return@mapNotNull null;val len=e.getElementsByTagNameNS("DAV:","getcontentlength").item(0)?.textContent?.toLongOrNull()?:0;val mod=e.getElementsByTagNameNS("DAV:","getlastmodified").item(0)?.textContent.orEmpty();RemoteBackup(name,href,len,mod)}}}
}
class WebDavException(message:String):Exception(message){companion object{fun safe(e:Exception)=WebDavException(when(e){is java.net.UnknownHostException->"无法解析服务器地址（DNS）";is java.net.SocketTimeoutException->"连接超时，请检查网络";is SSLException,is CertificateException->"TLS 证书或主机名校验失败";is java.net.ConnectException->"无法连接服务器";else->"网络请求失败"})}}
