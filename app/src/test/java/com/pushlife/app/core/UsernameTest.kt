package com.pushlife.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsernameTest {
    @Test fun cleanRemovesAtAccentsSpacesAndCase() {
        assertEquals("matheusastur", Username.clean("@Matheus Ástur"))
        assertEquals("joao_silva.10", Username.clean("João_Silva.10!"))
    }

    @Test fun cleanLimitsLength() {
        assertEquals(Username.MAX, Username.clean("a".repeat(40)).length)
    }

    @Test fun suggestStartsWithLetter() {
        assertEquals("ana", Username.suggest("12 Ana"))
        assertEquals("", Username.suggest("123"))
    }

    @Test fun problems() {
        assertEquals(Username.Problem.TOO_SHORT, Username.problem("ab"))
        assertEquals(Username.Problem.BAD_START, Username.problem("1abc"))
        assertEquals(Username.Problem.BAD_START, Username.problem("_abc"))
        assertNull(Username.problem("abc"))
        assertNull(Username.problem("pushlife_fan.2024"))
    }
}
