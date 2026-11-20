package app.drop.meta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PropertiesTest {
    @Test
    fun valuesAreStoredInOneForm() {
        assertEquals("1234.5", Properties.canonical(PropertyType.Number, "1 234,50"))
        assertEquals("0", Properties.canonical(PropertyType.Number, "-0,000"))
        assertNull(Properties.canonical(PropertyType.Number, "12abc"))
        assertEquals("2026-12-01", Properties.canonical(PropertyType.Date, " 2026-12-01 "))
        assertNull(Properties.canonical(PropertyType.Date, "2026-02-30"))
        assertEquals("true", Properties.canonical(PropertyType.Boolean, "Evet"))
        assertEquals("false", Properties.canonical(PropertyType.Boolean, "HAYIR".lowercase()))
        assertEquals("one two", Properties.canonical(PropertyType.Text, "one\n  two"))
        assertNull(Properties.canonical(PropertyType.Text, "x".repeat(501)))
    }

    @Test
    fun namesAreTrimmedAndKeysIgnoreCaseAndAccents() {
        assertEquals("Bitiş tarihi", Properties.cleanName("  Bitiş   tarihi "))
        assertNull(Properties.cleanName("   "))
        assertNull(Properties.cleanName("x".repeat(41)))
        assertEquals(Properties.key("Durum"), Properties.key("DURUM"))
        assertEquals(Properties.key("İş"), Properties.key("is"))
    }
}
