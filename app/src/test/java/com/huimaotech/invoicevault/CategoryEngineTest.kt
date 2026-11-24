package com.huimaotech.invoicevault

import org.junit.Assert.*
import org.junit.Test

class CategoryEngineTest{
 private fun expect(c:Category,item:String="",seller:String="")=assertEquals(c,CategoryEngine.classify(item,seller).category)
 @Test fun everyCategoryKeywords(){
  expect(Category.FOOD,"咖啡茶饮");expect(Category.TRANSPORT,"铁路火车票");expect(Category.LODGING,"酒店住宿费")
  expect(Category.COMMUNICATION,"中国移动话费");expect(Category.OFFICE,"办公用品打印耗材");expect(Category.SOFTWARE,"云服务订阅")
  expect(Category.LIFE,"家政生活服务");expect(Category.MEDICAL,"医院医疗费");expect(Category.EDUCATION,"教育培训课程")
  expect(Category.VEHICLE,"汽油加油服务");expect(Category.DIGITAL,"电脑电子产品");expect(Category.OTHER,"通用商品")
 }
 @Test fun realSampleIsSoftwareDespiteLifeConflict(){val r=CategoryEngine.classify("*生产生活服务*信息技术服务费","");assertEquals(Category.SOFTWARE,r.category);assertTrue(r.reason.contains("得分"))}
 @Test fun normalizationIgnoresSpacesPunctuationCaseAndCompanySuffix(){assertEquals("星巴克",CategoryEngine.normalizeSeller(" 星 巴 克（上海）有限公司 ").replace("上海",""));expect(Category.SOFTWARE,seller="ABC-云 服务有限公司")}
 @Test fun conflictUsesScoreNotFirstHit(){val r=CategoryEngine.classify("餐饮 咖啡 软件 云服务 订阅","");assertEquals(Category.SOFTWARE,r.category);assertTrue(r.confidence>=55)}
 @Test fun invoiceHeaderNumbersDoNotClassify(){val r=CategoryEngine.classify("","","发票号码 1234567890 税率 6% 合计 100.00");assertEquals(Category.OTHER,r.category)}
 @Test fun merchantRuleAlwaysWins(){val rule=MerchantCategoryRule("滴滴", "滴滴",Category.EDUCATION.id);val r=CategoryEngine.classify("网约车","滴滴",rule=rule);assertEquals(Category.EDUCATION,r.category);assertEquals(CategorySource.RULE,r.source);assertEquals(100,r.confidence)}
 @Test fun manualSurvivesRecognitionUnlessExplicitForce(){val old=InvoiceEntity(categoryId=Category.FOOD.id,categorySource=CategorySource.MANUAL.name);val d=CategoryDecision(Category.SOFTWARE,90,"软件",CategorySource.AUTO);assertEquals(Category.FOOD.id,CategoryEngine.applyUnlessManual(old,d).categoryId);assertEquals(Category.SOFTWARE.id,CategoryEngine.applyUnlessManual(old,d,true).categoryId)}
 @Test fun batchOnlyTakesUncategorized(){val rows=listOf(InvoiceEntity(id=1),InvoiceEntity(id=2,categoryId=Category.FOOD.id));assertEquals(listOf(1L),CategoryEngine.batchTargets(rows).map{it.id})}
}
