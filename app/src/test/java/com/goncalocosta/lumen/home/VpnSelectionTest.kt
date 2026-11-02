package com.goncalocosta.lumen.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VpnSelectionTest {
    @Test
    fun picksNordVpnWhenAvailableByDefault() {
        val installed = listOf("com.wireguard.android", "com.nordvpn.android", "net.openvpn.openvpn")
        val chosen = resolveSelectedVpnPackage(saved = null, installed = installed)
        assertEquals("com.nordvpn.android", chosen)
    }

    @Test
    fun honorsUserPreferredVpnIfInstalled() {
        val installed = listOf("com.wireguard.android", "com.nordvpn.android")
        val chosen = resolveSelectedVpnPackage(saved = "com.wireguard.android", installed = installed)
        assertEquals("com.wireguard.android", chosen)
    }

    @Test
    fun fallsBackToNordVpnIfSavedPreferenceWasUninstalled() {
        val installed = listOf("com.nordvpn.android", "net.openvpn.openvpn")
        val chosen = resolveSelectedVpnPackage(saved = "com.wireguard.android", installed = installed)
        assertEquals("com.nordvpn.android", chosen)
    }

    @Test
    fun fallsBackToFirstInstalledIfNordVpnNotPresent() {
        val installed = listOf("ch.protonvpn.android", "com.wireguard.android")
        val chosen = resolveSelectedVpnPackage(saved = null, installed = installed)
        assertEquals("ch.protonvpn.android", chosen)
    }

    @Test
    fun returnsNullWhenNoVpnInstalledAndNoSavedPref() {
        val chosen = resolveSelectedVpnPackage(saved = null, installed = emptyList())
        assertNull(chosen)
    }
}
