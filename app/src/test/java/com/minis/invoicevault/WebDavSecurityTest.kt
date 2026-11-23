package com.minis.invoicevault

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import javax.crypto.KeyGenerator

class WebDavSecurityTest {
 @Test fun urlAndPathNormalization(){assertEquals("https://example.com/dav",WebDavRules.normalizeUrl(" HTTPS://Example.COM/dav/ "));assertEquals("/InvoiceVault/",WebDavRules.normalizePath("/InvoiceVault/"));listOf("file:///tmp/x","content://x/y","http://example.com").forEach{assertThrows(IllegalArgumentException::class.java){WebDavRules.normalizeUrl(it)}};assertThrows(IllegalArgumentException::class.java){WebDavRules.normalizePath("/safe/../secret")};assertThrows(IllegalArgumentException::class.java){WebDavRules.normalizePath("/safe/%2e%2e/secret")}}
 @Test fun aesGcmRoundTripAndTamperDetection(){val k=KeyGenerator.getInstance("AES").apply{init(256)}.generateKey();val data=AesGcmCodec.encrypt(k,"secret-config".toByteArray());assertEquals("secret-config",String(AesGcmCodec.decrypt(k,data)));data[data.lastIndex]=(data.last().toInt() xor 1).toByte();assertThrows(Exception::class.java){AesGcmCodec.decrypt(k,data)}}
 @Test fun manifestSerializationAndChecksums(){val sums=mapOf("database/invoice-vault.db" to "a".repeat(64),"originals/a.pdf" to "b".repeat(64));val m=BackupManifest(createdAt=1,checksums=sums);val decoded=Gson().fromJson(Gson().toJson(m),BackupManifest::class.java);assertEquals("invoice-vault-full-backup",decoded.format);assertEquals(3,decoded.schemaVersion);assertEquals(sums,decoded.checksums)}
 @Test fun backupFilenameAndZipSlipRules(){val name=SecureBackup.backupFileName("invoice-vault",java.util.Date(0));assertTrue(name.matches(Regex("invoice-vault-\\d{8}-\\d{6}-\\d{3}\\.zip")));assertTrue(SecureBackup.isSafeZipPath("originals/a.pdf"));listOf("../x","a/../x","/absolute","a\\b").forEach{assertFalse(SecureBackup.isSafeZipPath(it))}}
 @Test fun parsesNamespacedWebDavXml(){val xml="""<?xml version="1.0"?><d:multistatus xmlns:d="DAV:"><d:response><d:href>/InvoiceVault/invoice-vault-20260101-010101.zip</d:href><d:propstat><d:prop><d:getcontentlength>1234</d:getcontentlength><d:getlastmodified>Thu, 01 Jan 2026 01:01:01 GMT</d:getlastmodified></d:prop></d:propstat></d:response></d:multistatus>""";val r=WebDavClient.parseMultiStatus(ByteArrayInputStream(xml.toByteArray()));assertEquals(1,r.size);assertEquals(1234,r[0].size);assertTrue(r[0].name.endsWith(".zip"))}
}
