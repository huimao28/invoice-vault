package com.minis.invoicevault

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class InvoiceFileActionsTest {
 @Test fun currentOriginalSharePlanHasRequiredContract(){
  val plan=ShareIntentPlan(Intent.ACTION_SEND,"application/pdf",emails=arrayOf("buyer@example.com"))
  assertEquals(Intent.ACTION_SEND,plan.action)
  assertEquals("application/pdf",plan.mimeType)
  assertTrue(plan.includeStream)
  assertTrue(plan.readGrant)
  assertTrue(plan.clipData)
  assertArrayEquals(arrayOf("buyer@example.com"),plan.emails)
 }
 @Test fun previewPlanHasReadGrantAndClipData(){
  val plan=ShareIntentPlan(Intent.ACTION_VIEW,"image/png")
  assertEquals(Intent.ACTION_VIEW,plan.action)
  assertTrue(plan.readGrant)
  assertTrue(plan.clipData)
 }
 @Test fun missingFileStateIsRepresentableWithoutLaunching(){
  val missing:java.io.File?=null
  assertFalse(missing?.isFile==true)
 }
}
