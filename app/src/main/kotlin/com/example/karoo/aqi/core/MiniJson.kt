package com.example.karoo.aqi.core

/**
 * Minimal, dependency-free JSON parser to keep `core` pure Kotlin.
 *
 * Supports: objects, arrays, strings, numbers, booleans, null.
 * Does not support: comments, trailing commas, NaN/Infinity.
 */
internal object MiniJson {
    fun parseToAny(json: String): Any? = Parser(json).parseValue()

    private class Parser(private val s: String) {
        private var i: Int = 0

        fun parseValue(): Any? {
            skipWs()
            if (i >= s.length) error("Unexpected end of input")
            return when (val c = s[i]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't' -> parseLiteral("true", true)
                'f' -> parseLiteral("false", false)
                'n' -> parseLiteral("null", null)
                '-', in '0'..'9' -> parseNumber()
                else -> error("Unexpected char '$c' at $i")
            }
        }

        private fun parseObject(): Map<String, Any?> {
            expect('{')
            skipWs()
            if (peek('}')) {
                i++
                return emptyMap()
            }
            val out = LinkedHashMap<String, Any?>()
            while (true) {
                skipWs()
                val key = parseString()
                skipWs()
                expect(':')
                val value = parseValue()
                out[key] = value
                skipWs()
                when {
                    peek(',') -> {
                        i++
                        continue
                    }
                    peek('}') -> {
                        i++
                        return out
                    }
                    else -> error("Expected ',' or '}' at $i")
                }
            }
        }

        private fun parseArray(): List<Any?> {
            expect('[')
            skipWs()
            if (peek(']')) {
                i++
                return emptyList()
            }
            val out = ArrayList<Any?>()
            while (true) {
                val v = parseValue()
                out.add(v)
                skipWs()
                when {
                    peek(',') -> {
                        i++
                        continue
                    }
                    peek(']') -> {
                        i++
                        return out
                    }
                    else -> error("Expected ',' or ']' at $i")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) error("Unterminated string")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) error("Unterminated escape")
                        val e = s[i++]
                        when (e) {
                            '"', '\\', '/' -> sb.append(e)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) error("Bad unicode escape at $i")
                                val hex = s.substring(i, i + 4)
                                i += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("Bad escape '\\$e' at ${i - 1}")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Number {
            val start = i
            if (peek('-')) i++
            if (i >= s.length) error("Bad number at $start")
            if (peek('0')) {
                i++
            } else {
                if (!s[i].isDigit()) error("Bad number at $start")
                while (i < s.length && s[i].isDigit()) i++
            }
            var isFloat = false
            if (i < s.length && s[i] == '.') {
                isFloat = true
                i++
                if (i >= s.length || !s[i].isDigit()) error("Bad fraction at $start")
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                isFloat = true
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || !s[i].isDigit()) error("Bad exponent at $start")
                while (i < s.length && s[i].isDigit()) i++
            }
            val raw = s.substring(start, i)
            return if (isFloat) raw.toDouble() else raw.toLong()
        }

        private fun <T> parseLiteral(lit: String, v: T): T {
            if (!s.regionMatches(i, lit, 0, lit.length)) error("Expected '$lit' at $i")
            i += lit.length
            return v
        }

        private fun skipWs() {
            while (i < s.length) {
                when (s[i]) {
                    ' ', '\t', '\r', '\n' -> i++
                    else -> return
                }
            }
        }

        private fun expect(c: Char) {
            if (i >= s.length || s[i] != c) error("Expected '$c' at $i")
            i++
        }

        private fun peek(c: Char): Boolean = i < s.length && s[i] == c
    }
}

