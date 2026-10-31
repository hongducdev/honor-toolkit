package vn.hongducdev.honortoolkit.feature.update

private val Heading = Regex("""^\s{0,3}#{1,6}\s+""")
private val Bullet = Regex("""^(\s*)[-*+]\s+""")
private val Link = Regex("""\[([^\]]+)]\([^)]*\)""")
private val Emphasis = Regex("""(\*\*|__|`)""")

/** GitHub release notes are Markdown; the page shows plain text, so keep the words and drop the markup. */
fun releaseNotesToPlainText(markdown: String): String =
    markdown.lineSequence()
        .map { line ->
            line.replace(Heading, "")
                .replace(Bullet) { "${it.groupValues[1]}• " }
                .replace(Link) { it.groupValues[1] }
                .replace(Emphasis, "")
                .trimEnd()
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
