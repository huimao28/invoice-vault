package com.minis.invoicevault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfInvoiceRegressionTest {
 @Test fun parsesSyntheticInvoiceTextLayerLayout() {
  val text=checkNotNull(javaClass.getResourceAsStream("/fixtures/synthetic-invoice-regression.txt")).bufferedReader().use{it.readText()}
  val f=InvoiceFieldParser.parse(text)
  assertEquals("26000000000000000001",f.number)
  assertEquals("2026-01-02",f.date)
  assertEquals("星河示例科技有限公司",f.buyer)
  assertEquals("91110000TEST000001",f.buyerTaxId)
  assertEquals("云杉虚构服务有限公司",f.seller)
  assertEquals("91310000TEST000002",f.sellerTaxId)
  assertEquals("100.00",f.amount)
  assertEquals("6.00",f.tax)
  assertEquals("106.00",f.total)
  assertEquals("*信息技术服务*合成测试服务费",f.item)
  assertTrue(f.missingRequired().isEmpty())
 }
 @Test fun acceptsUsefulInvoiceTextAndRejectsEmptyLayer() {
  assertTrue(DocumentRecognizer.isUsefulTextLayer("电子发票 发票号码 12345678901234567890 购买方信息 销售方信息 纳税人识别号 123456789012345678"))
  assertTrue(!DocumentRecognizer.isUsefulTextLayer("page 1"))
 }
}
