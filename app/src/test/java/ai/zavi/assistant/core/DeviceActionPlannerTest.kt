package ai.zavi.assistant.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceActionPlannerTest {
    @Test
    fun callCommandOnlyCreatesDialAction() {
        val action = DeviceActionPlanner.plan("call +91 98765 43210")
        assertTrue(action is DeviceAction.Dial)
        assertEquals("+919876543210", (action as DeviceAction.Dial).number)
    }

    @Test
    fun smsCommandCreatesDraftAction() {
        val action = DeviceActionPlanner.plan("sms to +91 98765 43210 saying I will be late")
        assertTrue(action is DeviceAction.ComposeSms)
        assertEquals("I will be late", (action as DeviceAction.ComposeSms).message)
    }

    @Test
    fun whatsappCommandCreatesDraftAction() {
        val action = DeviceActionPlanner.plan("whatsapp +91 98765 43210 saying Hello")
        assertTrue(action is DeviceAction.OpenWhatsApp)
        assertEquals("Hello", (action as DeviceAction.OpenWhatsApp).message)
    }

    @Test
    fun unknownCommandIsNotTreatedAsDeviceAction() {
        assertNull(DeviceActionPlanner.plan("what is artificial intelligence"))
    }
}
