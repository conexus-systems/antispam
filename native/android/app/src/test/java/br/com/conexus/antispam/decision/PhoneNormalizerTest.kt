package br.com.conexus.antispam.decision

import org.junit.Assert.assertEquals
import org.junit.Test

/** Subconjunto de rust/antispam-core/src/phone/mod.rs usado pela decisão. */
class PhoneNormalizerTest {

    @Test
    fun testEmergency() {
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("190").kind)
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("192").kind)
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("193").kind)
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("+55190").kind)
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("0190").kind)
        assertEquals(NumberKind.EMERGENCY, PhoneNormalizer.normalize("911").kind)
    }

    @Test
    fun testMobile() {
        val result = PhoneNormalizer.normalize("+5511987654321")
        assertEquals(NumberKind.MOBILE, result.kind)
        assertEquals("+5511987654321", result.e164)
        assertEquals("11", result.ddd)
        assertEquals("55-11", result.shard)
    }

    @Test
    fun testLandline() {
        val result = PhoneNormalizer.normalize("+551132345678")
        assertEquals(NumberKind.LANDLINE, result.kind)
        assertEquals("+551132345678", result.e164)
    }

    @Test
    fun testTollFree() {
        val result = PhoneNormalizer.normalize("08001234567")
        assertEquals(NumberKind.TOLL_FREE, result.kind)
        assertEquals("+558001234567", result.e164)
    }

    @Test
    fun testTelemarketing() {
        assertEquals(NumberKind.TELEMARKETING, PhoneNormalizer.normalize("03031234567").kind)
    }

    @Test
    fun testHidden() {
        assertEquals(NumberKind.HIDDEN, PhoneNormalizer.normalize("").kind)
        assertEquals(NumberKind.HIDDEN, PhoneNormalizer.normalize("private").kind)
        assertEquals(NumberKind.HIDDEN, PhoneNormalizer.normalize("restricted").kind)
        assertEquals(NumberKind.HIDDEN, PhoneNormalizer.normalize("privado").kind)
        assertEquals(NumberKind.HIDDEN, PhoneNormalizer.normalize(null).kind)
    }

    @Test
    fun testLocalNoDdd() {
        val result = PhoneNormalizer.normalize("987654321", "11")
        assertEquals(NumberKind.MOBILE, result.kind)
        assertEquals("+5511987654321", result.e164)
    }

    @Test
    fun testLocalNoDddFallback() {
        assertEquals(NumberKind.LOCAL_NO_DDD, PhoneNormalizer.normalize("987654321").kind)
    }

    @Test
    fun testInternational() {
        val result = PhoneNormalizer.normalize("+14155551234")
        assertEquals(NumberKind.INTERNATIONAL, result.kind)
        assertEquals("+14155551234", result.e164)
        assertEquals("55-intl", result.shard)
    }

    @Test
    fun testShortCode() {
        assertEquals(NumberKind.SHORT_CODE, PhoneNormalizer.normalize("1234").kind)
    }
}
