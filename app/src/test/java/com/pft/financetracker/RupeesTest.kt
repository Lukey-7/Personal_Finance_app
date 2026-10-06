package com.pft.financetracker

import com.pft.financetracker.domain.model.Paise
import com.pft.financetracker.domain.model.Rupees
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one rupee format. If these change, every figure in the app changes with them. */
class RupeesTest {
    @Test fun groupsTheIndianWay() {
        assertEquals("0", Rupees.group(0))
        assertEquals("999", Rupees.group(999))
        assertEquals("1,000", Rupees.group(1_000))
        assertEquals("1,05,000", Rupees.group(1_05_000))
        assertEquals("12,34,567", Rupees.group(12_34_567))
        assertEquals("1,00,00,000", Rupees.group(1_00_00_000))
    }

    @Test fun paiseOnlyWhenPresent() {
        assertEquals("₹2,400", Rupees.format(2_400_00))
        assertEquals("₹2,400.50", Rupees.format(2_400_50))
        assertEquals("₹0.50", Rupees.format(50))
        assertEquals("₹0", Rupees.format(0))
    }

    @Test fun negativesKeepTheSignOutside() {
        assertEquals("-₹50", Rupees.format(-50_00))
        assertEquals("-₹1,05,000.05", Rupees.format(-1_05_000_05))
    }

    @Test fun modes() {
        assertEquals("₹2,401", Rupees.format(2_400_50, Paise.NEVER))     // rounds half up, as money() did
        assertEquals("₹2,400.00", Rupees.format(2_400_00, Paise.ALWAYS))
    }
}
