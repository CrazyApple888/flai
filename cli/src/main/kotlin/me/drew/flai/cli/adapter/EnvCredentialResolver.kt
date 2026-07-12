package me.drew.flai.cli.adapter

import me.drew.flai.domain.port.CredentialResolver

class EnvCredentialResolver(
    private val env: (String) -> String? = System::getenv,
) : CredentialResolver {

    override fun resolve(credentialId: String): String? {
        if (credentialId.isBlank()) {
            return null
        }
        val suffix = credentialId.uppercase().map { c ->
            if (c.isLetterOrDigit()) {
                c
            } else {
                '_'
            }
        }.joinToString("")
        return env("FLAI_CREDENTIAL_$suffix")?.takeIf { it.isNotBlank() }
    }
}
