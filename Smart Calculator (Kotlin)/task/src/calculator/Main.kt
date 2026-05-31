package calculator

import java.math.BigInteger

sealed class CalculatorException(message: String) : Exception(message) {
    class UnknownCommand : CalculatorException("Unknown command")
    class InvalidIdentifier : CalculatorException("Invalid identifier")
    class InvalidAssignment : CalculatorException("Invalid assignment")
    class UnknownVariable : CalculatorException("Unknown variable")
    class InvalidExpression : CalculatorException("Invalid expression")
    class DivisionByZero : CalculatorException("Division by zero")
    class ExponentTooLarge : CalculatorException("Exponent exceeds maximum allowed value")
}

enum class Operation(
    val precedence: Int,
    private val op: (BigInteger, BigInteger) -> BigInteger
) {
    ADD(1, { a, b -> a + b }),
    SUBTRACT(1, { a, b -> a - b }),
    MULTIPLY(2, { a, b -> a * b }),
    DIVIDE(2, { a, b ->
        if (b == BigInteger.ZERO) {
            throw CalculatorException.DivisionByZero()
        }
        a / b
    }),
    POWER(3, { a, b ->
        if (b < BigInteger.ZERO) throw CalculatorException.InvalidExpression()
        if (b > BigInteger.valueOf(MAX_SAFE_EXPONENT)) {
            throw CalculatorException.ExponentTooLarge()
        }

        a.pow(b.toInt())
    });

    fun apply(left: BigInteger, right: BigInteger): BigInteger = op(left, right)

    companion object {
        private const val MAX_SAFE_EXPONENT = 9999L
    }
}

sealed class Token {
    abstract val raw: String

    data class Number(override val raw: String, val value: BigInteger) : Token()
    data class Operator(override val raw: String, val operation: Operation) : Token()
    data class Variable(override val raw: String) : Token()
    data class OpeningParenthesis(override val raw: String) : Token()
    data class ClosingParenthesis(override val raw: String) : Token()
    data class Invalid(override val raw: String) : Token()

    companion object {
        fun from(input: String): Token {
            val rawInput = input.trim()
            val bigIntValue = rawInput.toBigIntegerOrNull()

            return when {
                bigIntValue != null -> Number(rawInput, bigIntValue)

                rawInput == "*" -> Operator(rawInput, Operation.MULTIPLY)

                rawInput == "/" -> Operator(rawInput, Operation.DIVIDE)

                rawInput == "^" -> Operator(rawInput, Operation.POWER)

                rawInput.matches(Regex("\\++")) -> Operator(rawInput, Operation.ADD)

                rawInput.matches(Regex("-+")) -> {
                    val op = if (rawInput.length % 2 != 0) Operation.SUBTRACT else Operation.ADD
                    Operator(rawInput, op)
                }

                rawInput.matches(Regex("[a-zA-Z]+")) ->
                    Variable(rawInput)

                else -> Invalid(rawInput)
            }
        }
    }
}

class Tokenizer(private var input: String) {

    private val numberRegex   = Regex("""^\d+""")
    private val plusMinusRegex   = Regex("""^[+\-]+""")
    private val operatorRegex = Regex("""^[+\-*/^]+""")
    private val openParenRegex = Regex("""^\(""")
    private val closeParenRegex = Regex("""^\)""")
    private val variableRegex = Regex("""^[a-zA-Z]+""")

    fun allTokens(): List<Token> {
        val tokens = mutableListOf<Token>()

        var token = next()

        while (token != null) {

            if (token is Token.Operator && (token.raw.startsWith("-"))) {
                // Insert a zero before a unary minus.
                if (tokens.isEmpty() || tokens.last() is Token.OpeningParenthesis) {
                    tokens.add(Token.Number("0", BigInteger.ZERO))
                }
            }

            tokens.add(token)
            token = next()
        }

        return tokens
    }

    fun next(): Token? {
        input = input.trimStart()
        if (input.isEmpty()) return null

        val token = numberRegex.find(input)?.let { match ->
            val raw = match.value

            input = input.drop(raw.length)

            Token.Number(raw, raw.toBigInteger())
        } ?: openParenRegex.find(input)?.let { match ->
            val raw = match.value

            input = input.drop(raw.length)

            Token.OpeningParenthesis(raw)
        } ?: closeParenRegex.find(input)?.let { match ->
            val raw = match.value

            input = input.drop(raw.length)

            Token.ClosingParenthesis(raw)
        } ?: plusMinusRegex.find(input)?.let { match ->
            // Special case for plug-minus-chains.
            val raw = match.value
            input = input.drop(raw.length)

            val minusCount = raw.count { it == '-' }
            val operator = if (minusCount % 2 != 0) Operation.SUBTRACT else Operation.ADD

            Token.Operator(raw, operator)
        } ?: operatorRegex.find(input)?.let { match ->
            val raw = match.value

            input = input.drop(raw.length)

            val operator = when (raw) {
                "+" -> Operation.ADD
                "-" -> Operation.SUBTRACT
                "*" -> Operation.MULTIPLY
                "/" -> Operation.DIVIDE
                "^" -> Operation.POWER
                else -> throw CalculatorException.InvalidExpression()
            }

            Token.Operator(raw, operator)
        } ?: variableRegex.find(input)?.let { match ->
            val raw = match.value

            input = input.drop(raw.length)

            Token.Variable(raw)
        } ?: throw CalculatorException.InvalidExpression()

        return token
    }
}

class Calculator {

    private val variables = mutableMapOf<String, BigInteger>()

    fun handleCommand(command: String): Boolean {
        var continueRunning = true

        when (command) {
            "help" -> printHelp()
            "exit" -> { println("Bye!") ; continueRunning = false }
            else -> throw CalculatorException.UnknownCommand()
        }

        return continueRunning
    }

    fun handleAssignment(leftSide: String, rightSide: String) {
        val variableToken = Token.from(leftSide)
        if (variableToken !is Token.Variable) throw CalculatorException.InvalidIdentifier()

        val value = try {
            evaluateExpression(rightSide)
        } catch (_: CalculatorException) {
            throw CalculatorException.InvalidAssignment()
        }

        this.variables[variableToken.raw] = value
    }

    fun convertToPostfixExpression(infixExpression: List<Token>): List<Token> {
        val result = mutableListOf<Token>()
        val stack = ArrayDeque<Token>()

        for (token in infixExpression) {
            when (token) {
                is Token.Number, is Token.Variable -> result.add(token)
                is Token.Operator -> {
                    // First push all non-weaker operators to the result.
                    while (stack.isNotEmpty() && stack.last() is Token.Operator) {
                        val topOperator = stack.last() as Token.Operator
                        if (topOperator.operation.precedence >= token.operation.precedence) {
                            result.add(stack.removeLast())
                        } else {
                            break
                        }
                    }

                    stack.addLast(token)
                }
                is Token.OpeningParenthesis -> stack.addLast(token)
                is Token.ClosingParenthesis -> {
                    var foundOpening = false

                    while (stack.isNotEmpty()) {
                        val popped = stack.removeLast()
                        if (popped is Token.OpeningParenthesis) {
                            foundOpening = true
                            break
                        } else {
                            result.add(popped)
                        }
                    }

                    if (!foundOpening) throw CalculatorException.InvalidExpression()
                }
                is Token.Invalid -> throw CalculatorException.InvalidExpression()
            }
        }

        while (stack.isNotEmpty()) {
            val popped = stack.removeLast()
            if (popped is Token.OpeningParenthesis || popped is Token.ClosingParenthesis) {
                throw CalculatorException.InvalidExpression()
            }
            result.add(popped)
        }

        return result
    }

    fun evaluatePostfixExpression(expression: List<Token>): BigInteger {
        val stack = ArrayDeque<BigInteger>()

        for (token in expression) {
            when (token) {
                is Token.Number -> stack.addLast(token.value)
                is Token.Variable -> {
                    val value = variables[token.raw] ?: throw CalculatorException.UnknownVariable()
                    stack.addLast(value)
                }
                is Token.Operator -> {
                    if (stack.size < 2) throw CalculatorException.InvalidExpression()

                    val right = stack.removeLast()
                    val left = stack.removeLast()
                    val result = token.operation.apply(left, right)

                    stack.addLast(result)
                }
                else -> throw CalculatorException.InvalidExpression()
            }
        }

        if (stack.size != 1) throw CalculatorException.InvalidExpression()

        return stack.removeLast()
    }

    fun evaluateExpression(expression: String): BigInteger {
        val tokens =Tokenizer(expression).allTokens()

        if (tokens.isEmpty()) throw CalculatorException.InvalidExpression()

        val postfixExpression = convertToPostfixExpression(tokens)

        return evaluatePostfixExpression(postfixExpression)
    }

    fun run() {
        while(true) {
            try {
                val input = readln()

                if (input.isBlank()) continue

                if (input.startsWith("/")) {
                    if (!handleCommand(input.drop(1))) break
                } else if (input.contains("=")) {
                    val indexOfEqualSign = input.indexOf("=")
                    val leftSide = input.substring(0, indexOfEqualSign)
                    val rightSide = input.substring(indexOfEqualSign + 1)

                    handleAssignment(leftSide, rightSide)
                } else {
                    val result = evaluateExpression(input)

                    println(result)
                }
            } catch (e: Exception) {
                println(e.message)
            }
        }
    }

    private fun printHelp() {
        println("""
        Smart Calculator
            
        SYNOPSIS
             [expression]            - Evaluates a math expression
             [variable] = [expr]     - Assigns a value or expression to a variable
             [variable]              - Prints the current value of a variable
             /[command]              - Executes a calculator command

        DESCRIPTION
             The program processes mathematical expressions in infix notation, 
             converts them to postfix format using the Shunting-Yard algorithm, 
             and evaluates the result. It supports standard arithmetic, variables, 
             nested parentheses, and smart unary operator resolution.

        FEATURES & SYNTAX
             Numbers
                     Integers of arbitrary lengths (e.g., 42, 1005).

             Variables
                     Identifiers consisting of Latin letters only (a-z, A-Z). 
                     Case-sensitive. Variables must be assigned an integer or a 
                     valid expression before use.
                     Example: n = 2 * a + 7

             Operators
                     +   Addition
                     -   Subtraction
                     *   Multiplication
                     /   Integer Division
                     ^   Power / Exponentiation
                     
             Unary Operators (Sign Support)
                     Expressions can safely start with a negative or positive sign, 
                     as well as contain signs right after parentheses.
                     Example: -5 + (-3 * 2) -> resolves to -11

             Parentheses
                     Expressions can be grouped using '(' and ')'. They enforce 
                     precedence over regular operator priority. Nested parentheses 
                     are fully supported.

        COMMANDS
             /help   Displays this manual page.
             /exit   Terminated the calculator application.

        DIAGNOSTICS
             Invalid identifier    Thrown if a variable assignment uses an invalid 
                                   name (e.g., containing digits).
             Invalid expression    Thrown on syntax errors, mismatched parentheses, 
                                   or consecutive forbidden operators (*, /, ^).
             Unknown variable      Thrown if an unassigned variable is used in 
                                   an expression.
    """.trimIndent())
    }
}

fun main() {
    Calculator().run()
}
