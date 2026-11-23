/*
 * Copyright (c) 2026 Christians Martínez Alvarado
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mardous.booming.data.remote.nas

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.SecurityUtils
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Android ships an old BouncyCastle provider under the "BC" name that lacks
 * X25519 and EC key agreement. sshj prefers that provider, so swap it for the
 * modern BouncyCastle bundled with the APK before creating SSH clients.
 */
internal fun newSshClient(): SSHClient {
    ensureBouncyCastleProvider()
    return SSHClient()
}

private fun ensureBouncyCastleProvider() {
    val current = Security.getProvider("BC")
    if (current == null ||
        current.javaClass.name != BouncyCastleProvider::class.java.name
    ) {
        runCatching { Security.removeProvider("BC") }
        runCatching { Security.insertProviderAt(BouncyCastleProvider(), 1) }
    }
    runCatching {
        SecurityUtils.registerSecurityProvider(BouncyCastleProvider::class.java.name)
    }
}
