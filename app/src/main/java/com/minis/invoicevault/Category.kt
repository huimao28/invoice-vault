package com.minis.invoicevault

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Stable, locale-independent category identifiers persisted in Room and exports. */
enum class Category(val id:String,val title:String,val iconKey:String,val colorKey:String){
 FOOD("food","餐饮","restaurant","orange"),
 TRANSPORT("transport","交通出行","directions_car","blue"),
 LODGING("lodging","住宿","hotel","indigo"),
 COMMUNICATION("communication","通讯","phone","cyan"),
 OFFICE("office","办公用品","inventory","brown"),
 SOFTWARE("software_subscription","软件与订阅","cloud","purple"),
 LIFE("life_services","生活服务","home_repair","green"),
 MEDICAL("medical_health","医疗健康","medical","red"),
 EDUCATION("education_training","教育培训","school","teal"),
 VEHICLE("vehicle","车辆相关","local_gas","amber"),
 DIGITAL("digital_appliance","数码家电","devices","deep_blue"),
 OTHER("other","其他","category","gray");
 companion object{fun fromId(id:String)=entries.firstOrNull{it.id==id}}
}
enum class CategorySource{AUTO,RULE,MANUAL,UNKNOWN}
data class CategoryDecision(val category:Category,val confidence:Int,val reason:String,val source:CategorySource)

@Entity(tableName="merchant_category_rules",indices=[Index(value=["normalizedSeller"],unique=true)])
data class MerchantCategoryRule(@PrimaryKey val normalizedSeller:String,val sellerDisplay:String,val categoryId:String,val updatedAt:Long=System.currentTimeMillis())

object CategoryEngine{
 private data class Signal(val words:List<String>,val category:Category,val weight:Int)
 private val itemSignals=listOf(
  Signal(listOf("餐饮","饭店","餐厅","食品","外卖","咖啡","茶饮"),Category.FOOD,38),
  Signal(listOf("出租车","网约车","滴滴","客运","铁路","航空","机票","火车票","通行费"),Category.TRANSPORT,42),
  Signal(listOf("酒店","宾馆","住宿","民宿"),Category.LODGING,44),
  Signal(listOf("电信","移动","联通","话费","通信服务"),Category.COMMUNICATION,42),
  Signal(listOf("办公用品","文具","打印","耗材"),Category.OFFICE,40),
  Signal(listOf("信息技术服务","技术服务","云服务","软件","会员","订阅","网络服务"),Category.SOFTWARE,45),
  Signal(listOf("家政","物业","生活服务"),Category.LIFE,34),
  Signal(listOf("医院","诊所","药房","医药","医疗"),Category.MEDICAL,44),
  Signal(listOf("教育","培训","学费","课程"),Category.EDUCATION,43),
  Signal(listOf("汽油","柴油","加油","停车","充电服务","维修保养","汽车服务"),Category.VEHICLE,44),
  Signal(listOf("电脑","手机","数码","家电","电子产品"),Category.DIGITAL,42)
 )
 private val sellerSignals=itemSignals.map{it.copy(weight=(it.weight*.72).toInt())}
 fun normalizeSeller(value:String):String=value.uppercase().replace(Regex("[\\s\\p{P}\\p{S}]+"),"").replace(Regex("(?:有限责任公司|股份有限公司|有限公司|集团公司|集团|公司|商贸|经营部|分店|门店)$"),"")
 fun classify(itemContent:String,seller:String,rawText:String="",rule:MerchantCategoryRule?=null):CategoryDecision{
  if(rule!=null)Category.fromId(rule.categoryId)?.let{return CategoryDecision(it,100,"商家规则：${rule.sellerDisplay.ifBlank{seller}}",CategorySource.RULE)}
  val item=normalizeText(itemContent);val merchant=normalizeText(seller)
  val scores=linkedMapOf<Category,Int>();val reasons=linkedMapOf<Category,MutableList<String>>()
  fun apply(text:String,signals:List<Signal>,label:String){signals.forEach{s->s.words.filter{text.contains(normalizeText(it))}.distinct().forEach{w->scores[s.category]=(scores[s.category]?:0)+s.weight;reasons.getOrPut(s.category){mutableListOf()}.add("$label“$w”")}}}
  apply(item,itemSignals,"商品");apply(merchant,sellerSignals,"商家")
  // Raw OCR is deliberately weak and only considered when structured fields carry no signal.
  if(scores.isEmpty()&&(item.isNotBlank()||merchant.isNotBlank())) apply(normalizeText(rawText.take(2000)),itemSignals.map{it.copy(weight=12)},"识别文字")
  val ranked=scores.entries.sortedByDescending{it.value};val best=ranked.firstOrNull()
  if(best==null)return CategoryDecision(Category.OTHER,20,"未发现可靠的商品或商家关键词",CategorySource.AUTO)
  val runner=ranked.getOrNull(1)?.value?:0;val margin=best.value-runner
  val confidence=(55+best.value.coerceAtMost(35)+margin.coerceAtMost(10)).coerceIn(0,96)
  return CategoryDecision(best.key,confidence,"${reasons[best.key].orEmpty().joinToString("、")}；得分 ${best.value}${if(runner>0)"，领先 $margin" else ""}",CategorySource.AUTO)
 }
 private fun normalizeText(s:String)=s.uppercase().replace(Regex("[\\s\\p{P}\\p{S}]+"),"")
 fun applyUnlessManual(invoice:InvoiceEntity,decision:CategoryDecision,force:Boolean=false)=if(invoice.categorySource==CategorySource.MANUAL.name&&!force)invoice else invoice.copy(categoryId=decision.category.id,categorySource=decision.source.name,categoryConfidence=decision.confidence,categoryReason=decision.reason)
 fun uncategorized(v:InvoiceEntity)=Category.fromId(v.categoryId)==null
 fun batchTargets(rows:List<InvoiceEntity>)=rows.filter(::uncategorized)
}
