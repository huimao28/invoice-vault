package com.minis.invoicevault

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

@Entity(tableName="invoices", indices=[Index(value=["sha256"], unique=true), Index(value=["identityKey"]),Index(value=["categoryId"])])
data class InvoiceEntity(
 @PrimaryKey(autoGenerate=true) val id:Long=0, val number:String="", val code:String="", val date:String="",
 val seller:String="", val sellerTaxId:String="", val amountCents:Long=0, val taxCents:Long=0, val totalCents:Long=0,
 val source:String="", val sha256:String="", val identityKey:String="", val status:String="待确认",
 val originalValues:String="{}", val storedName:String="", val mimeType:String="", val createdAt:Long=System.currentTimeMillis(),
 val buyer:String="", val buyerTaxId:String="", val itemContent:String="", val rawText:String="", val lastError:String="", val recognizedAt:Long=0,
 val categoryId:String="",val categorySource:String=CategorySource.UNKNOWN.name,val categoryConfidence:Int=0,val categoryReason:String=""
)

data class Totals(val count:Long, val amount:Long?, val tax:Long?, val total:Long?)
@Dao interface InvoiceDao {
 @Query("SELECT * FROM invoices ORDER BY date DESC, createdAt DESC") fun observeAll():Flow<List<InvoiceEntity>>
 @Query("SELECT COUNT(*) count, SUM(amountCents) amount, SUM(taxCents) tax, SUM(totalCents) total FROM invoices") fun observeTotals():Flow<Totals>
 @Query("SELECT * FROM invoices WHERE id=:id LIMIT 1") suspend fun byId(id:Long):InvoiceEntity?
 @Query("SELECT * FROM invoices WHERE sha256=:hash LIMIT 1") suspend fun byHash(hash:String):InvoiceEntity?
 @Query("SELECT * FROM invoices WHERE identityKey=:key AND identityKey<>'' AND id<>:excludeId LIMIT 1") suspend fun byIdentity(key:String,excludeId:Long=0):InvoiceEntity?
 @Query("SELECT * FROM invoices") suspend fun allNow():List<InvoiceEntity>
 @Insert suspend fun insert(v:InvoiceEntity):Long
 @Update suspend fun update(v:InvoiceEntity)
 @Delete suspend fun delete(v:InvoiceEntity)
 @Query("SELECT * FROM merchant_category_rules WHERE normalizedSeller=:seller LIMIT 1") suspend fun rule(seller:String):MerchantCategoryRule?
 @Query("SELECT * FROM merchant_category_rules ORDER BY sellerDisplay") fun observeRules():Flow<List<MerchantCategoryRule>>
 @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun upsertRule(v:MerchantCategoryRule)
 @Query("DELETE FROM merchant_category_rules WHERE normalizedSeller=:seller") suspend fun deleteRule(seller:String)
}
@Database(entities=[InvoiceEntity::class,MerchantCategoryRule::class],version=3,exportSchema=false)
abstract class VaultDatabase:RoomDatabase(){ abstract fun dao():InvoiceDao
 companion object {
  val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("ALTER TABLE invoices ADD COLUMN buyer TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN buyerTaxId TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN itemContent TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN rawText TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN lastError TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN recognizedAt INTEGER NOT NULL DEFAULT 0")
  }}
  val MIGRATION_2_3=object:Migration(2,3){override fun migrate(db:SupportSQLiteDatabase){
   db.execSQL("ALTER TABLE invoices ADD COLUMN categoryId TEXT NOT NULL DEFAULT ''");db.execSQL("ALTER TABLE invoices ADD COLUMN categorySource TEXT NOT NULL DEFAULT 'UNKNOWN'");db.execSQL("ALTER TABLE invoices ADD COLUMN categoryConfidence INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE invoices ADD COLUMN categoryReason TEXT NOT NULL DEFAULT ''");db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_categoryId ON invoices(categoryId)");db.execSQL("CREATE TABLE IF NOT EXISTS merchant_category_rules (normalizedSeller TEXT NOT NULL, sellerDisplay TEXT NOT NULL, categoryId TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(normalizedSeller))");db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_merchant_category_rules_normalizedSeller ON merchant_category_rules(normalizedSeller)")
  }}
 }
}
object RecognitionPolicy {
 fun fieldsEmpty(v:InvoiceEntity)=v.number.isBlank()&&v.code.isBlank()&&v.date.isBlank()&&v.seller.isBlank()&&v.sellerTaxId.isBlank()&&v.amountCents==0L&&v.taxCents==0L&&v.totalCents==0L
 fun shouldReRecognize(v:InvoiceEntity)=fieldsEmpty(v)||v.status.contains("图片未OCR")||v.status.contains("解析失败")||v.status.contains("识别失败")||(v.mimeType=="application/pdf"&&!v.rawText.startsWith("【识别来源："))
 fun status(raw:String,missing:List<String>)=when { raw.isBlank()->"待确认·图片未OCR";missing.isNotEmpty()->"待确认·已读取文字但字段不完整";else->"已识别" }
}
object InvoiceLogic {
 fun norm(s:String)=s.trim().uppercase().replace(Regex("[^\\p{L}\\p{N}]"),"")
 fun identity(code:String, number:String, sellerTaxId:String):String {val c=norm(code);val n=norm(number);val t=norm(sellerTaxId);return when{c.isNotEmpty()&&n.isNotEmpty()->"C:$c|N:$n";n.isNotEmpty()&&t.isNotEmpty()->"N:$n|T:$t";else->""}}
 fun money(text:String):Long=try{BigDecimal(text.replace(",","").replace("¥","").trim()).movePointRight(2).setScale(0,java.math.RoundingMode.HALF_UP).longValueExact()}catch(_:Exception){0}
 fun csv(value:String)="\""+value.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\""
 fun similar(a:InvoiceEntity,b:InvoiceEntity):Boolean {val seller=norm(a.seller)==norm(b.seller)&&a.seller.isNotBlank();val total=kotlin.math.abs(a.totalCents-b.totalCents)<=1;val date=a.date==b.date&&a.date.isNotBlank();return listOf(seller,total,date).count{it}>=2}
 fun totals(rows:List<InvoiceEntity>)=Totals(rows.size.toLong(),rows.sumOf{it.amountCents},rows.sumOf{it.taxCents},rows.sumOf{it.totalCents})
}
