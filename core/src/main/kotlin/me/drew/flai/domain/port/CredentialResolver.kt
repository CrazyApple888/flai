package me.drew.flai.domain.port

fun interface CredentialResolver {
    fun resolve(credentialId: String): String?
}
