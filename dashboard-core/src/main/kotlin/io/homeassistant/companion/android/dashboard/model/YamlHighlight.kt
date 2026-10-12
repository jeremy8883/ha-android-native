package io.homeassistant.companion.android.dashboard.model

// The YAML colours of `ha-code-editor` (frontend@20260624.6 src/resources/codemirror.ts: `haHighlightStyle` over
// @lezer/yaml's tags, and `yamlScalarHighlighter` typing the plain scalars), for text [yamlDump] wrote.

/**
 * A kind of YAML token and its colour: the CodeMirror variable, its default (`haHighlightStyle`'s fallback, the
 * light theme's) and the dark theme's value (src/resources/theme/color/color.globals.ts `darkColorStyles`);
 * [PLAIN] has none and takes the text colour.
 */
enum class YamlTokenKind(val variable: String?, val lightArgb: Long?, val darkArgb: Long?) {
    /** Spaces, and block scalars' content (`tags.content`, unstyled). */
    PLAIN(null, null, null),

    /** A mapping key (`definition(propertyName)`). */
    KEY("codemirror-property", PROPERTY_LIGHT, PROPERTY_DARK),

    /** `:` and `-` (`separator`), `{}` and `[]` (`brace`, `squareBracket`). */
    SEPARATOR("codemirror-def", DEF_LIGHT, DEF_DARK),

    /** A quoted scalar (`string`) or a plain one of no other type (`.yaml-string`). */
    STRING("codemirror-string", STRING_LIGHT, STRING_DARK),

    /** A plain integer or float (`.yaml-number`). */
    NUMBER("codemirror-number", NUMBER_LIGHT, NUMBER_DARK),

    /** A plain boolean or null (`.yaml-bool`, `.yaml-null`). */
    ATOM("codemirror-atom", ATOM_LIGHT, ATOM_DARK),

    /** A block scalar's header, `|-` (`special(string)`, the operator colour). */
    BLOCK_HEADER("codemirror-operator", OPERATOR_LIGHT, OPERATOR_DARK),
}

/** A piece of a line of YAML and its [kind]. */
data class YamlToken(val text: String, val kind: YamlTokenKind)

/**
 * The tokens of each line of [yaml] (as [yamlDump] writes it: block mappings and sequences, quoted or plain
 * scalars, block scalars), coloured as `ha-code-editor` colours them.
 */
fun yamlHighlight(yaml: String): List<List<YamlToken>> {
    // A block scalar's lines are those more indented than the line of its header
    var blockIndent: Int? = null
    return yaml.split('\n').map { line ->
        val indent = line.takeWhile { it == ' ' }.length
        val inBlock = blockIndent?.let { line.isBlank() || indent > it } == true
        if (inBlock) {
            listOf(YamlToken(line, YamlTokenKind.PLAIN))
        } else {
            blockIndent = null
            val tokens = lineTokens(line, indent)
            if (tokens.lastOrNull()?.kind == YamlTokenKind.BLOCK_HEADER) blockIndent = indent
            tokens
        }
    }.map { tokens -> tokens.filter { it.text.isNotEmpty() } }
}

private fun lineTokens(line: String, indent: Int): List<YamlToken> {
    val tokens = mutableListOf(YamlToken(line.take(indent), YamlTokenKind.PLAIN))
    var rest = line.drop(indent)
    while (rest == "-" || rest.startsWith("- ")) {
        tokens += YamlToken("-", YamlTokenKind.SEPARATOR)
        val spaces = rest.drop(1).takeWhile { it == ' ' }
        tokens += YamlToken(spaces, YamlTokenKind.PLAIN)
        rest = rest.drop(1 + spaces.length)
    }
    keyLength(rest)?.let { length ->
        tokens += YamlToken(rest.take(length), YamlTokenKind.KEY)
        tokens += YamlToken(":", YamlTokenKind.SEPARATOR)
        val spaces = rest.drop(length + 1).takeWhile { it == ' ' }
        tokens += YamlToken(spaces, YamlTokenKind.PLAIN)
        rest = rest.drop(length + 1 + spaces.length)
    }
    if (rest.isNotEmpty()) tokens += YamlToken(rest, valueKind(rest))
    return tokens
}

/** The length of the key [text] starts with (before its `:`), `null` when it is a value. */
private fun keyLength(text: String): Int? {
    val end = when (text.firstOrNull()) {
        '"', '\'' -> quotedLength(text)
        else -> text.indexOf(": ").takeIf { it > 0 } ?: text.length.takeIf { text.endsWith(':') }?.minus(1)
    } ?: return null
    val colon = text.getOrNull(end) == ':' && (end + 1 == text.length || text[end + 1] == ' ')
    return end.takeIf { colon && it > 0 }
}

/** The length of the quoted scalar [text] starts with, quotes included; `null` when it isn't closed. */
private fun quotedLength(text: String): Int? {
    val quote = text[0]
    var index = 1
    while (index < text.length) {
        val c = text[index]
        when {
            quote == '"' && c == '\\' -> index++
            c == quote && quote == '\'' && text.getOrNull(index + 1) == '\'' -> index++
            c == quote -> return index + 1
        }
        index++
    }
    return null
}

private fun valueKind(value: String): YamlTokenKind = when {
    value.startsWith('"') || value.startsWith('\'') -> YamlTokenKind.STRING
    value == "{}" || value == "[]" -> YamlTokenKind.SEPARATOR
    BLOCK_HEADER.matches(value) -> YamlTokenKind.BLOCK_HEADER
    YAML_BOOL.matches(value) || YAML_NULL.matches(value) -> YamlTokenKind.ATOM
    YAML_INT.matches(value) || YAML_FLOAT.matches(value) -> YamlTokenKind.NUMBER
    else -> YamlTokenKind.STRING
}

private val BLOCK_HEADER = Regex("""^[|>][0-9]?[+-]?$""")

// The scalar types of `yamlScalarHighlighter`
private val YAML_BOOL = Regex("^(?:true|True|TRUE|false|False|FALSE|yes|Yes|YES|no|No|NO|on|On|ON|off|Off|OFF)$")
private val YAML_NULL = Regex("^(?:~|null|Null|NULL)$")
private val YAML_INT = Regex("^(?:[+-]?(?:0|[1-9][0-9]*)(?:_[0-9]+)*|0o[0-7]+|0x[0-9a-fA-F]+)$")
private val YAML_FLOAT =
    Regex("""^(?:[+-]?(?:[0-9][0-9_]*)?\.[0-9.]*(?:[eE][+-]?[0-9]+)?|[+-]?\.(?:inf|Inf|INF)|\.(?:nan|NaN|NAN))$""")

// The CodeMirror colours: `haHighlightStyle`'s fallbacks, and `darkColorStyles`' values
private const val PROPERTY_LIGHT = 0xFF990055L
private const val PROPERTY_DARK = 0xFFC792EAL
private const val DEF_LIGHT = 0xFF8DA6CEL
private const val DEF_DARK = 0xFF82AAFFL
private const val STRING_LIGHT = 0xFF0077AAL
private const val STRING_DARK = 0xFFC3E88DL
private const val NUMBER_LIGHT = 0xFFCA7841L
private const val NUMBER_DARK = 0xFFFF5370L
private const val ATOM_LIGHT = 0xFFFF9900L
private const val ATOM_DARK = 0xFFF78C6CL
private const val OPERATOR_LIGHT = 0xFFCDA869L
private const val OPERATOR_DARK = 0xFF89DDFFL
