package ai.zavi.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorEngineTest {
    @Test
    fun respectsArithmeticPrecedence() {
        assertEquals("14", CalculatorEngine.tryCalculate("2 + 3 * 4"))
    }

    @Test
    fun supportsParenthesesAndUnaryMinus() {
        assertEquals("-20", CalculatorEngine.tryCalculate("calculate -(2 + 3) * 4"))
    }

    @Test
    fun supportsNaturalOperatorWords() {
        assertEquals("42", CalculatorEngine.tryCalculate("what is 6 times 7"))
    }

    @Test
    fun rejectsNonMathQuestionsAndDivisionByZero() {
        assertNull(CalculatorEngine.tryCalculate("what is artificial intelligence"))
        assertNull(CalculatorEngine.tryCalculate("calculate 5 / 0"))
    }
}
