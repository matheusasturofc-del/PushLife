package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val url = "https://pushlife.br.uptodown.com/android"

    @Test fun olderVersionMustUpdate() {
        assertTrue(UpdatePolicy.mustUpdate(120, UpdateInfo(121, url)))
    }

    @Test fun sameOrNewerVersionIsFree() {
        assertFalse(UpdatePolicy.mustUpdate(121, UpdateInfo(121, url)))
        assertFalse(UpdatePolicy.mustUpdate(130, UpdateInfo(121, url)))
    }

    @Test fun noInfoOrNoLinkNeverLocks() {
        assertFalse(UpdatePolicy.mustUpdate(1, null))
        assertFalse(UpdatePolicy.mustUpdate(1, UpdateInfo(999, " ")))
    }

    @Test fun notesFallBackToEnglishThenAny() {
        val info = UpdateInfo(1, url, mapOf("en" to "New", "es" to "Nuevo"))
        assertEquals("Nuevo", UpdatePolicy.notesFor(info, "es"))
        assertEquals("New", UpdatePolicy.notesFor(info, "pt"))
        assertEquals("Só pt", UpdatePolicy.notesFor(UpdateInfo(1, url, mapOf("pt" to "Só pt")), "es"))
        assertNull(UpdatePolicy.notesFor(UpdateInfo(1, url), "pt"))
    }
}
