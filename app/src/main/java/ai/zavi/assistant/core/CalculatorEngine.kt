package ai.zavi.assistant.core

import java.math.BigDecimal

object CalculatorEngine {
    fun tryCalculate(query: String): String? {
        var expression = query.trim()
        val prefixes = listOf("calculate ", "compute ", "solve ", "what is ", "what's ")
        val lower = expression.lowercase()
        val prefix = prefixes.firstOrNull { lower.startsWith(it) }
        if (prefix != null) expression = expression.drop(prefix.length)

        expression = expression
            .replace(Regex("\\bmultiplied by\\b", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("\\btimes\\b", RegexOption.IGNORE_CASE), "*")
            .replace(Regex("\\bdivided by\\b", RegexOption.IGNORE_CASE), "/")
            .replace(Regex("\\bplus\\b", RegexOption.IGNORE_CASE), "+")
            .replace(Regex("\\bminus\\b", RegexOption.IGNORE_CASE), "-")
            .replace(Regex("\\bmod(?:ulo)?\\b", RegexOption.IGNORE_CASE), "%")
            .trim()

        if (expression.isBlank()) return null
        if (!Regex("^[0-9+\\-*/%.()\\s]+$").matches(expression)) return null
        if (!expression.any { it in "+-*/%" }) return null

        return try {
            val value = Parser(expression).parse()
            if (!value.isFinite()) return null
            BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        } catch (_: Exception) {
            null
        }
    }

    private class Parser(private val input: String) {
        private var position = 0

        fun parse(): Double {
            val result = expression()
            skipSpaces()
            require(position == input.length) { "Unexpected input" }
            require(result.isFinite()) { "Invalid result" }
            return result
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when {
                    consume('+') -> value + term()
                    consume('-') -> value - term()
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = unary()
            while (true) {
                skipSpaces()
                value = when {
                    consume('*') -> value * unary()
                    consume('/') -> {
                        val divisor = unary()
                        require(divisor != 0.0) { "Division by zero" }
                        value / divisor
                    }
                    consume('%') -> {
                        val divisor = unary()
                        require(divisor != 0.0) { "Division by zero" }
                        value % divisor
                    }
                    else -> return value
                }
            }
        }

        private fun unary(): Double {
            skipSpaces()
            return when {
                consume('+') -> unary()
                consume('-') -> -unary()
                else -> primary()
            }
        }

        private fun primary(): Double {
            skipSpaces()
            if (consume('(')) {
                val value = expression()
                skipSpaces()
                require(consume(')')) { "Missing closing bracket" }
                return value
            }

            val start = position
            var dotSeen = false
            var digitSeen = false
            while (position < input.length) {
                val char = input[position]
                when {
                    char.isDigit() -> {
                        digitSeen = true
                        position++
                    }
                    char == '.' && !dotSeen -> {
                        dotSeen = true
                        position++
                    }
                    else -> break
                }
            }
            require(digitSeen) { "Number expected" }
            return input.substring(start, position).toDouble()
        }

        private fun consume(char: Char): Boolean {
            if (position < input.length && input[position] == char) {
                position++
                return true
            }
            return false
        }

        private fun skipSpaces() {
            while (position < input.length && input[position].isWhitespace()) position++
        }
    }
}
