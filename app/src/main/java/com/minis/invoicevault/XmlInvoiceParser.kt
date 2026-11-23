package com.minis.invoicevault
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

object XmlInvoiceParser {
 private val aliases=mapOf(
  "number" to setOf("invoicenumber","invoiceno","fphm","number"),
  "code" to setOf("invoicecode","fpdm","code"),
  "date" to setOf("issuedate","kprq","invoicedate","billingdate"),
  "buyer" to setOf("buyername","gfmc","purchasername"),
  "buyerTaxId" to setOf("buyertaxid","gfsbh","purchasertaxid"),
  "seller" to setOf("sellername","xfmc","vendorname"),
  "taxid" to setOf("sellertaxid","xfsbh","xfsh","vendortaxid"),
  "amount" to setOf("amount","amountwithouttax","hjje","je","totaltaxexclusive","totalexclusive"),
  "tax" to setOf("taxamount","totaltax","hjse","se"),
  "item" to setOf("itemname","goodsname","xmmc","content","description"),
  "total" to setOf("totalamount","jshj","total","totaltaxincluded","totaltax-inclusive","amountwithtax","grandtotal"))
 fun parse(input:InputStream):Map<String,String> {
  val bytes=input.readBytes();require(bytes.size<=10*1024*1024){"XML过大"}
  val raw=bytes.toString(Charsets.UTF_8);require(!raw.contains("<!DOCTYPE",true)&&!raw.contains("<!ENTITY",true)){"禁止DTD/实体"}
  val parser=Xml.newPullParser();parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);parser.setInput(raw.reader())
  val out=linkedMapOf<String,String>();var event=parser.eventType;var tag=""
  while(event!=XmlPullParser.END_DOCUMENT){when(event){XmlPullParser.START_TAG->tag=(parser.name?:"").substringAfter(':').lowercase();XmlPullParser.TEXT->{val value=parser.text.trim();if(value.isNotEmpty())aliases.entries.firstOrNull{tag in it.value}?.let{if(out[it.key].isNullOrBlank())out[it.key]=value}}};event=parser.next()}
  out["raw"]=raw
  return out
 }
}
