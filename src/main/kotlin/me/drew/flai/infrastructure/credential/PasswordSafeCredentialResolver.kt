package me.drew.flai.infrastructure.credential

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import me.drew.flai.domain.port.CredentialResolver

class PasswordSafeCredentialResolver : CredentialResolver {
    override fun resolve(credentialId: String): String? {
        val attrs = CredentialAttributes(generateServiceName("flai", credentialId))
        return PasswordSafe.instance.getPassword(attrs)
    }
}
