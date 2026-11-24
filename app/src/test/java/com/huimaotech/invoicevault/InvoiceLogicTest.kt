package com.huimaotech.invoicevault
import org.junit.Assert.*
import org.junit.Test
class InvoiceLogicTest {
 @Test fun totals_areExact(){val t=InvoiceLogic.totals(listOf(InvoiceEntity(amountCents=100,taxCents=6,totalCents=106),InvoiceEntity(amountCents=200,taxCents=12,totalCents=212)));assertEquals(2,t.count);assertEquals(300L,t.amount);assertEquals(18L,t.tax);assertEquals(318L,t.total)}
 @Test fun identity_normalizes(){assertEquals("C:0123|N:AB88",InvoiceLogic.identity(" 01-23 ","ab 88",""));assertEquals("N:9|T:TAX1",InvoiceLogic.identity("","9","tax-1"));assertEquals("",InvoiceLogic.identity("","9",""))}
 @Test fun duplicateSimilarity_requiresTwoSignals(){val a=InvoiceEntity(seller="甲公司",date="2026-01-01",totalCents=100);assertTrue(InvoiceLogic.similar(a,a.copy(id=2)));assertFalse(InvoiceLogic.similar(a,a.copy(id=2,seller="乙",date="2026-02-01")))}
 @Test fun csvEscapesQuotesAndLines(){assertEquals("\"a\"\"b c\"",InvoiceLogic.csv("a\"b\nc"))}
 @Test fun moneyUsesCents(){assertEquals(12346,InvoiceLogic.money("¥123.456"))}
 @Test fun parsesDigitalInvoice(){val f=InvoiceFieldParser.parse("""电子发票（增值税专用发票）
发票号码：24312000000123456789  开票日期：2026年10月08日
购买方信息 名称：上海甲科技有限公司 纳税人识别号：91310000MA12345678
销售方信息 名称：北京乙服务有限公司 纳税人识别号：91110108MA87654321
不含税金额：¥1,000.00 税额：¥60.00
价税合计（小写）：¥1,060.00""");assertEquals("24312000000123456789",f.number);assertEquals("2026-10-08",f.date);assertEquals("北京乙服务有限公司",f.seller);assertEquals("1060.00",f.total);assertEquals(106000,InvoiceLogic.money(f.total))}
 @Test fun parsesVatElectronicOrdinaryInvoice(){val f=InvoiceFieldParser.parse("""增值税电子普通发票
发票代码: 031002200111 发票号码: 87654321
开票日期:2025-12-19
购买方 名称:杭州采购有限公司 税号:913301001234567890
销售方 名称:深圳销售有限公司 统一社会信用代码:91440300123456789X
金额合计 ¥888.88 税额合计 ¥53.33 价税合计(小写) ¥942.21""");assertEquals("031002200111",f.code);assertEquals("87654321",f.number);assertEquals("深圳销售有限公司",f.seller);assertEquals("888.88",f.amount);assertEquals("53.33",f.tax);assertEquals("942.21",f.total)}
 @Test fun parsesOcrSpacingAndSymbolNoise(){val f=InvoiceFieldParser.parse("""发 票 号 码 ： 2 4 1 1 0 0 0 0 1 2 3 4 5 6 7 8 9 0 1 2
开 票 日 期 ： 2026 年 1 月 9 日
销 售 方 信 息  名 称 ： 南 京 丙 商 贸 有 限 公 司
纳 税 人 识 别 号 ： 9 1 3 2 0 1 0 0 M A 1 A B 2 C 3 4 D
不 含 税 金 额 ： Y 2,345．67 税 额 ： Y140．74
价 税 合 计 （ 小 写 ） ： ￥2，486．41""");assertEquals("24110000123456789012",f.number);assertEquals("2026-01-09",f.date);assertEquals("2486.41",f.total);assertEquals(248641,InvoiceLogic.money(f.total))}
 @Test fun parsesFlattenedDigitalInvoiceText(){val f=InvoiceFieldParser.parse("""【识别来源：PDF文本层】
电子发票（普通发票） 发票号码: 26127000000987654321
开票日期: 2026年11月21日
购 名称：海州市星河商务有限公司 销 名称：滨江市云帆科技有限公司
买 售
方 方
信 统一社会信用代码/纳税人识别号：91320000MA12ABCD3E 信 统一社会信用代码/纳税人识别号：91440000MA98WXYZ7K
息 息
项目名称 规格型号 单 位 数 量 单 价 金 额 税率/征收率 税 额
*生产生活服务*信息技 次 1 3.21 3.21 6% 0.19
术服务费
合 计 ¥3.21 ¥0.19
价 税 合 计 （大 写） 叁圆肆角整 （小 写） ¥3.40""");assertEquals("26127000000987654321",f.number);assertEquals("2026-11-21",f.date);assertEquals("海州市星河商务有限公司",f.buyer);assertEquals("滨江市云帆科技有限公司",f.seller);assertEquals("91320000MA12ABCD3E",f.buyerTaxId);assertEquals("91440000MA98WXYZ7K",f.sellerTaxId);assertEquals("*生产生活服务*信息技术服务费",f.item);assertEquals("3.21",f.amount);assertEquals("0.19",f.tax);assertEquals("3.40",f.total);assertTrue(f.missingRequired().isEmpty())}
 @Test fun totalIsNeverInvented(){val f=InvoiceFieldParser.parse("发票号码:12345678 金额:100.00 税额:6.00");assertEquals("",f.total)}
 @Test fun legacyBlankRecordMustBeReRecognized(){assertTrue(RecognitionPolicy.shouldReRecognize(InvoiceEntity(id=7,storedName="old.jpg",status="待确认")));assertTrue(RecognitionPolicy.shouldReRecognize(InvoiceEntity(number="12345678",status="待确认·解析失败")))}
 @Test fun recognizedDuplicateMustBeSkipped(){assertFalse(RecognitionPolicy.shouldReRecognize(InvoiceEntity(number="12345678",date="2026-01-01",seller="甲",totalCents=100,status="已识别")))}
 @Test fun ocrTextWithoutFieldsHasHonestStatus(){assertEquals("待确认·已读取文字但字段不完整",RecognitionPolicy.status("有一些文字",listOf("发票号码")));assertEquals("待确认·图片未OCR",RecognitionPolicy.status("",listOf("发票号码")))}
 @Test fun replacementPreservesIdentityAndMapsBuyer(){val old=InvoiceEntity(id=42,sha256="abc",storedName="x.png");val f=InvoiceFieldParser.parse("购买方 名称:甲公司 税号:913301001234567890 销售方 名称:乙公司 税号:91440300123456789X");val replaced=old.copy(buyer=f.buyer,seller=f.seller);assertEquals(42,replaced.id);assertEquals("abc",replaced.sha256);assertEquals("x.png",replaced.storedName);assertEquals("甲公司",replaced.buyer)}
}
