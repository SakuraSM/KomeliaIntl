package snd.webview

private val cssTokens = Regex(
    """/\*[\s\S]*?\*/|url\(\s*(?:\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'|[^)]*)\s*\)|\"(?:\\.|[^\"\\])*\"|'(?:\\.|[^'\\])*'""",
    RegexOption.IGNORE_CASE,
)
private val cssUrl = Regex("""^(url\(\s*)([\"']?)([^\"')\s]+)(\2)(\s*\))$""", RegexOption.IGNORE_CASE)

/** Changes URL tokens without changing CSS strings or comments. */
fun transformCssUrls(css: String, transform: (String) -> String): String = cssTokens.replace(css) { token ->
    val match = cssUrl.matchEntire(token.value)
    if (match == null) token.value else {
        match.groupValues[1] + match.groupValues[2] + transform(match.groupValues[3]) + match.groupValues[4] + match.groupValues[5]
    }
}
