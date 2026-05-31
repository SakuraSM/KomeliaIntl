package snd.komelia.ui.common

private const val HTTP_SCHEME = "http://"
private const val HTTPS_SCHEME = "https://"
private const val MIN_SERVER_PORT = 1
private const val MAX_SERVER_PORT = 65535

fun validateServerUrl(serverUrl: String): ServerUrlValidationError? {
    val authority = serverUrl.getAuthority() ?: return ServerUrlValidationError.INVALID_URL
    if (authority.isBlank()) return ServerUrlValidationError.INVALID_URL
    if (!authority.hasHost()) return ServerUrlValidationError.INVALID_URL

    val port = authority.getExplicitPort() ?: return null
    return if (port in MIN_SERVER_PORT..MAX_SERVER_PORT) null
    else ServerUrlValidationError.INVALID_PORT
}

enum class ServerUrlValidationError {
    INVALID_URL,
    INVALID_PORT
}

private fun String.getAuthority(): String? {
    val schemeEndIndex = when {
        startsWith(HTTP_SCHEME) -> HTTP_SCHEME.length
        startsWith(HTTPS_SCHEME) -> HTTPS_SCHEME.length
        else -> return null
    }
    val authorityAndPath = drop(schemeEndIndex)
    return authorityAndPath.substringBefore('/').substringBefore('?').substringBefore('#')
}

private fun String.hasHost(): Boolean {
    if (startsWith("[")) return indexOf(']') > 1
    return substringBefore(':').isNotBlank()
}

private fun String.getExplicitPort(): Int? {
    if (startsWith("[")) return getBracketedHostPort()

    val portSeparatorCount = count { it == ':' }
    if (portSeparatorCount == 0) return null
    if (portSeparatorCount > 1) return null

    val portText = substringAfter(':')
    return portText.toValidServerPort()
}

private fun String.getBracketedHostPort(): Int? {
    val hostEndIndex = indexOf(']')
    if (hostEndIndex < 0) return null

    val portText = drop(hostEndIndex + 1)
    if (portText.isEmpty()) return null
    if (!portText.startsWith(":")) return null

    return portText.drop(1).toValidServerPort()
}

private fun String.toValidServerPort(): Int? {
    if (isBlank() || any { !it.isDigit() }) return Int.MIN_VALUE
    return toIntOrNull() ?: Int.MIN_VALUE
}
