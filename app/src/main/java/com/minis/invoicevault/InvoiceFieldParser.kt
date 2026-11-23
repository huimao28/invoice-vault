package com.minis.invoicevault

/** Parser for standard Chinese digital/VAT invoices. It uses labels and layout order, never vendor identities. */
object InvoiceFieldParser {
 data class Fields(val number:String="",val code:String="",val date:String="",val buyer:String="",val buyerTaxId:String="",val seller:String="",val sellerTaxId:String="",val amount:String="",val tax:String="",val total:String="",val item:String="",val rawText:String="") {
  fun asMap():Map<String,String> = linkedMapOf("number" to number,"code" to code,"date" to date,"buyer" to buyer,"buyerTaxId" to buyerTaxId,"seller" to seller,"taxid" to sellerTaxId,"amount" to amount,"tax" to tax,"total" to total,"item" to item,"raw" to rawText).filterValues{it.isNotBlank()}
  fun missingRequired():List<String> = buildList {if(number.isBlank())add("发票号码");if(date.isBlank())add("开票日期");if(total.isBlank())add("价税合计")}
 }
 fun parse(raw:String):Fields {
  var text=raw.replace('\u00a0',' ').replace('：',':').replace('，',',').replace('￥','¥').replace('（','(').replace('）',')')
  val labels=listOf("发票号码","票据号码","发票代码","开票日期","开具日期","购买方信息","购方信息","购买方","购方","销售方信息","销方信息","销售方","销方","纳税人识别号","统一社会信用代码","不含税金额","金额合计","合计金额","税额合计","合计税额","价税合计","小写","项目名称","名称","税号")
  labels.forEach{label->text=text.replace(Regex(label.map{Regex.escape(it.toString())}.joinToString("[ \\t]*")),label)}
  val compact=text.replace(Regex("[ \\t]"),"")
  fun first(vararg patterns:String,source:String=text)=patterns.asSequence().mapNotNull{Regex(it,setOf(RegexOption.IGNORE_CASE,RegexOption.MULTILINE)).find(source)?.groupValues?.getOrNull(1)}.map(::clean).firstOrNull{it.isNotBlank()}.orEmpty()
  fun digits(label:String,min:Int,max:Int)=first("$label\\s*:?\\s*([0-9OIl](?:[0-9OIl\\- ]{0,58}[0-9OIl])?)").replace(Regex("[^0-9OIl]"),"").replace('O','0').replace('I','1').replace('l','1').takeIf{it.length in min..max}.orEmpty()
  val number=digits("(?:发票号码|票据号码|No\\.?)",8,20);val code=digits("(?:发票代码|票据代码)",10,14)
  val date=normalizeDate(first("(?:开票日期|开具日期)\\s*:?\\s*([0-9OIl]{4}[年./\\-][0-9OIl]{1,2}[月./\\-][0-9OIl]{1,2}日?)",source=compact))

  // A flattened PDF commonly emits: "购 名称:X 销 名称:Y".  Keep the right marker
  // as the boundary, rather than relying on column whitespace (which PDF extraction loses).
  val paired=Regex("(?:购(?:买)?方?|购买方)\\s*名称\\s*:?\\s*(.+?)\\s+(?:销(?:售)?方?|销售方)\\s*名称\\s*:?\\s*([^\\r\\n]+)").find(text)
  fun partyName(s:String)=s.replace(Regex("(?:统一社会信用代码(?:/纳税人识别号)?|纳税人识别号|税号).*$"),"").trim().replace(Regex("(?<=\\p{IsHan})\\s+(?=\\p{IsHan})"),"")
  val pairedBuyer=paired?.groupValues?.get(1)?.let(::partyName).orEmpty()
  val pairedSeller=paired?.groupValues?.get(2)?.let(::partyName).orEmpty()

  // Traditional left/right text layers may instead contain two bare 名称 cells.
  val nameRow=text.lineSequence().map{line->Regex("(?:名称|名\\s*称)\\s*:?\\s*(.+)").find(line)?.groupValues?.get(1).orEmpty()}.firstOrNull{Regex("\\s{2,}").containsMatchIn(it)}.orEmpty()
  val rowNames=nameRow.split(Regex("\\s{2,}")).map{partyName(it.replace(Regex("^(?:购|销)?\\s*(?:名称)?\\s*:?\\s*"),""))}.filter{it.length>=2&&!it.contains("识别号")}
  val allTaxIds=Regex("(?:统一社会信用代码(?:/纳税人识别号)?|纳税人识别号|税号)\\s*:?\\s*([0-9A-Z]{15,20})").findAll(text.uppercase()).map{it.groupValues[1]}.toList()
  fun sectionParty(section:String,next:String):Pair<String,String>{
   val block=Regex("$section[\\s\\S]{0,260}?(?=$next|项目名称|货物或应税劳务|$)",RegexOption.IGNORE_CASE).find(text)?.value.orEmpty()
   val name=partyName(first("(?:名称|名\\s*称)\\s*:?\\s*([^\\n\\r]{2,80})",source=block))
   val tax=first("(?:纳税人识别号|统一社会信用代码(?:/纳税人识别号)?|税号)\\s*:?\\s*([0-9A-Z OIl\\-]{15,30})",source=block).replace(Regex("[^0-9A-Z]"),"").replace('O','0').replace('I','1')
   return name to tax
  }
  val buyerSection=sectionParty("(?:购买方信息|购方信息|购买方|购方|购)","(?:销售方信息|销方信息|销售方|销方|销)")
  val sellerSection=sectionParty("(?:销售方信息|销方信息|销售方|销方|销)","(?:备注|开票人|项目名称|合计|价税合计)")
  val buyerName=pairedBuyer.ifBlank{rowNames.getOrNull(0).orEmpty().ifBlank{buyerSection.first}}
  val sellerName=pairedSeller.ifBlank{rowNames.getOrNull(1).orEmpty().ifBlank{sellerSection.first}}
  // On standard invoices the tax-id row has the same left-to-right buyer/seller order.
  val buyerTax=allTaxIds.getOrNull(0).orEmpty().ifBlank{buyerSection.second}
  val sellerTax=allTaxIds.getOrNull(1).orEmpty().ifBlank{sellerSection.second}

  val total=normalizeMoney(first("价税合计(?:\\(小写\\))?\\s*:?\\s*[¥Y]?\\s*([0-9OIl,.．]+)","价税合计[\\s\\S]{0,80}?\\(小写\\)\\s*:?\\s*[¥Y]?\\s*([0-9OIl,.．]+)",source=compact))
  val sumLine=text.lineSequence().firstOrNull{Regex("合\\s*计").containsMatchIn(it)&&!it.contains("价税合计")}.orEmpty()
  val sumMoney=Regex("[¥￥]\\s*([0-9OIl,.．]+)").findAll(sumLine).map{normalizeMoney(it.groupValues[1])}.filter{it.isNotBlank()}.toList()
  fun moneyAfter(label:String)=normalizeMoney(first("$label\\s*(?:\\(小写\\))?\\s*:?\\s*[¥Y]?\\s*([0-9OIl,.．]+)"))
  val amount=sumMoney.getOrNull(0)?:moneyAfter("(?:不含税金额|金额合计|合计金额)");val tax=sumMoney.getOrNull(1)?:moneyAfter("(?:税额合计|合计税额|税额)")

  val lines=text.lines();val header=lines.indexOfFirst{it.contains("项目名称")||it.contains("货物或应税劳务")};val sum=lines.indexOfFirst{it.contains("合")&&it.contains("计")&&!it.contains("价税合计")}
  val item=if(header>=0&&sum>header) rebuildItem(lines.subList(header+1,sum)) else ""
  return Fields(number,code,date,buyerName,buyerTax,sellerName,sellerTax,amount,tax,total,item,raw)
 }
 private fun rebuildItem(detail:List<String>):String {
  val fragments=mutableListOf<String>()
  // A data row ends in quantity/unit-price/amount/rate/tax. The first whitespace-delimited
  // field is the project name; following physical lines without columns continue that name.
  val dataTail=Regex("\\s+\\S{1,12}\\s+[-0-9OIl.]+\\s+[-0-9OIl.]+\\s+[-0-9OIl.]+\\s+[-0-9OIl.]+%\\s+[-0-9OIl.]+\\s*$")
  detail.forEach { rawLine ->
   val line=rawLine.trim()
   if(line.isBlank()||line.contains("规格型号")||line.contains("税率/征收率")||line.contains("项目名称")) return@forEach
   val m=dataTail.find(line)
   val fragment=if(m!=null) line.substring(0,m.range.first).trim() else if(Regex("[¥￥]|^合\\s*计").containsMatchIn(line)) "" else line
   if(fragment.isNotBlank()) fragments += fragment.replace(Regex("\\s+"),"")
  }
  return fragments.joinToString("")
 }
 private fun clean(s:String)=s.trim().trim(':','：',';','；').replace(Regex("[ \\t]+")," ")
 private fun normalizeMoney(s:String):String {val x=s.replace(",","").replace("，","").replace('．','.').replace('O','0').replace('I','1').replace('l','1').replace(Regex("[^0-9.]"),"");return if(Regex("\\d+(?:\\.\\d{1,2})?").matches(x))x else ""}
 private fun normalizeDate(s:String):String {val x=s.replace('O','0').replace('I','1').replace('l','1');val m=Regex("(\\d{4})[年./-](\\d{1,2})[月./-](\\d{1,2})").find(x)?:return "";return "%s-%02d-%02d".format(m.groupValues[1],m.groupValues[2].toInt(),m.groupValues[3].toInt())}
}
