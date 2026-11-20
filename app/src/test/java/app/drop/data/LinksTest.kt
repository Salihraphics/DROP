package app.drop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {
    @Test
    fun findsTheFirstRealLinkWithoutSentencePunctuation() {
        assertEquals("https://example.com/a", Links.find("Read https://example.com/a."))
        assertEquals("https://en.wikipedia.org/wiki/Foo_(bar)", Links.find("See (https://en.wikipedia.org/wiki/Foo_(bar))!"))
        assertEquals("www.drop.app", Links.find("Visit www.drop.app, today"))
        assertNull(Links.find("mail me@www.example.com or http://localhost"))
        assertEquals("https://b.org", Links.find("no link", "but https://b.org here"))
    }

    @Test
    fun hostsAreLowercaseWithoutWww() {
        assertEquals("example.com", Links.host("https://WWW.Example.com:8080/x?y#z"))
        assertEquals("example.com", Links.host("www.example.com"))
        assertNull(Links.host("ftp://example.com"))
        assertNull(Links.host("https://-bad.com"))
        assertNull(Links.host("https://nodot"))
    }
}
