package com.attendpro.core

import org.junit.Assert.*
import org.junit.Test

class CredentialHash1980Test {
    @Test fun saltedHashAcceptsCorrectAndRejectsWrongCredential() {
        val first = CredentialHash1980.hash("735921")
        val second = CredentialHash1980.hash("735921")
        assertTrue(first.startsWith("pbkdf2-sha256:"))
        assertNotEquals(first, second)
        assertTrue(CredentialHash1980.verify(first, "735921"))
        assertFalse(CredentialHash1980.verify(first, "735922"))
        assertFalse(CredentialHash1980.needsUpgrade(first))
    }

    @Test fun legacySha256CredentialRemainsReadableForMigration() {
        val legacy = PairingProtocol.pinHash("246810")
        assertTrue(CredentialHash1980.needsUpgrade(legacy))
        assertTrue(CredentialHash1980.verify(legacy, "246810"))
        assertFalse(CredentialHash1980.verify(legacy, "246811"))
    }
}
