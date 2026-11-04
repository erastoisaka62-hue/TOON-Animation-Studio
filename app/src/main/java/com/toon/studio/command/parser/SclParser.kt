package com.toon.studio.command.parser

import com.toon.studio.command.model.SclBlock
import com.toon.studio.command.model.SclCategory
import com.toon.studio.command.model.SclDiagnostic
import com.toon.studio.command.model.SclDocument
import com.toon.studio.command.model.SclField
import com.toon.studio.command.model.SclSeverity
import com.toon.studio.command.model.SclValue

private val NUMBER = Regex("-?[0-9]+(\\.[0-9]+)?")

/**
 * Reads MRP-SCL text into an [SclDocument].
 *
 * Syntax:  CATEGORY { KEY: value  KEY: "text" }
 * Comments start with # or //. Fields may be separated by new lines, commas or ;
 * The parser never throws: problems become diagnostics with a line number,
 * and parsing continues so several errors can be reported at once.
 */
object SclParser {

    fun parse(source: String): SclDocument {
        // Phone keyboards often produce curly quotes and non-breaking spaces.
        val clean = source
            .replace('\u201C', '"')
            .replace('\u201D', '"')
            .replace('\u00A0', ' ')
        return Scanner(clean).run()
    }

    private class Scanner(private val s: String) {
        private var pos = 0
        private var line = 1
        private val blocks = ArrayList<SclBlock>()
        private val diags = ArrayList<SclDiagnostic>()

        private fun atEnd(): Boolean = pos >= s.length

        private fun peek(): Char = if (pos < s.length) s[pos] else '\u0000'

        private fun peekAt(offset: Int): Char =
            if (pos + offset < s.length) s[pos + offset] else '\u0000'

        private fun advance(): Char {
            val c = s[pos]
            pos++
            if (c == '\n') line++
            return c
        }

        private fun addError(message: String, ln: Int, code: String) {
            diags.add(SclDiagnostic(SclSeverity.ERROR, message, ln, code))
        }

        private fun addWarning(message: String, ln: Int, code: String) {
            diags.add(SclDiagnostic(SclSeverity.WARNING, message, ln, code))
        }

        private fun isIdentStart(c: Char): Boolean = c.isLetter() || c == '_'

        private fun isIdentPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

        private fun readIdent(): String {
            val start = pos
            while (!atEnd() && isIdentPart(peek())) advance()
            return s.substring(start, pos)
        }

        /** Skips spaces and comments. Also skips new lines when [newlines] is true. */
        private fun skipTrivia(newlines: Boolean) {
            while (!atEnd()) {
                val c = peek()
                if (c == ' ' || c == '\t' || c == '\r') {
                    advance()
                } else if (c == '\n') {
                    if (newlines) advance() else return
                } else if (c == '#' || (c == '/' && peekAt(1) == '/')) {
                    while (!atEnd() && peek() != '\n') advance()
                } else {
                    return
                }
            }
        }

        private fun skipToEndOfLine() {
            while (!atEnd() && peek() != '\n') advance()
        }

        /** Like skipToEndOfLine, but stops before a closing brace. */
        private fun skipToFieldEnd() {
            while (!atEnd() && peek() != '\n' && peek() != '}') advance()
        }

        fun run(): SclDocument {
            if (s.startsWith("\uFEFF")) pos = 1
            while (true) {
                skipTrivia(true)
                if (atEnd()) break
                val c = peek()
                if (isIdentStart(c)) {
                    parseBlock()
                } else if (c == '}') {
                    addError("'}' imepatikana bila block iliyofunguliwa.", line, "STRAY_BRACE")
                    advance()
                } else {
                    addError(
                        "Herufi '$c' haikutarajiwa hapa. Anza na jina la command, mfano SCENE {",
                        line,
                        "UNEXPECTED_CHAR"
                    )
                    skipToEndOfLine()
                }
            }
            return SclDocument(blocks, diags)
        }

        private fun parseBlock() {
            val startLine = line
            val name = readIdent()
            val category = SclCategory.fromName(name)
            if (category == null) {
                val known = SclCategory.values().joinToString(", ") { it.name }
                addError("Command '$name' haitambuliki. Zinazotambulika: $known.", startLine, "UNKNOWN_CATEGORY")
            }

            val savePos = pos
            val saveLine = line
            skipTrivia(true)
            if (peek() != '{') {
                pos = savePos
                line = saveLine
                addError("Baada ya '$name' inatakiwa '{'.", startLine, "MISSING_OPEN_BRACE")
                skipToEndOfLine()
                return
            }
            advance() // '{'

            val fields = ArrayList<SclField>()
            var closed = false
            var endLine = startLine
            while (true) {
                skipTrivia(true)
                if (atEnd()) break
                val c = peek()
                if (c == '}') {
                    endLine = line
                    advance()
                    closed = true
                    break
                }
                if (c == ',' || c == ';') {
                    advance()
                    continue
                }
                if (isIdentStart(c)) {
                    parseField(fields)
                    continue
                }
                addError("Herufi '$c' haikutarajiwa ndani ya $name. Andika KEY: value.", line, "UNEXPECTED_CHAR")
                skipToFieldEnd()
            }
            if (!closed) {
                addError("Block '$name' haijafungwa; inakosa '}'.", startLine, "UNCLOSED_BLOCK")
                endLine = line
            }
            if (category != null) {
                blocks.add(SclBlock(category, fields, startLine, endLine))
            }
        }

        private fun parseField(fields: MutableList<SclField>) {
            val ln = line
            val key = readIdent().uppercase()
            skipTrivia(false)
            if (peek() != ':') {
                addError("Baada ya '$key' inatakiwa ':' kisha thamani. Mfano: $key: \"...\"", ln, "MISSING_COLON")
                skipToFieldEnd()
                return
            }
            advance() // ':'
            skipTrivia(false)
            val value = readValue(key, ln) ?: return
            if (fields.any { it.key == key }) {
                addWarning("Field '$key' imerudiwa; thamani ya kwanza ndiyo inatumika.", ln, "DUPLICATE_FIELD")
            }
            fields.add(SclField(key, value, ln))
        }

        private fun readValue(key: String, ln: Int): SclValue? {
            if (atEnd() || peek() == '\n' || peek() == '}') {
                addError("Field '$key' haina thamani.", ln, "MISSING_VALUE")
                return null
            }
            if (peek() == '"') return readString(ln)
            val start = pos
            while (!atEnd()) {
                val c = peek()
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == ',' || c == ';' || c == '}') break
                pos++
            }
            val token = s.substring(start, pos)
            return if (NUMBER.matches(token)) {
                SclValue.Num(token.toDouble(), token)
            } else {
                SclValue.Word(token)
            }
        }

        private fun readString(ln: Int): SclValue {
            advance() // opening quote
            val sb = StringBuilder()
            while (true) {
                if (atEnd() || peek() == '\n') {
                    addError("Maandishi yaliyoanza kwa \" hayajafungwa kwenye mstari huo.", ln, "UNTERMINATED_STRING")
                    break
                }
                val c = advance()
                if (c == '"') break
                if (c == '\\' && !atEnd() && peek() != '\n') {
                    val n = advance()
                    when (n) {
                        'n' -> sb.append('\n')
                        't' -> sb.append('\t')
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        else -> {
                            sb.append('\\')
                            sb.append(n)
                        }
                    }
                } else {
                    sb.append(c)
                }
            }
            return SclValue.Text(sb.toString())
        }
    }
}
