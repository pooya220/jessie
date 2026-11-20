package ir.jessi.finance.ui

import org.junit.Assert.*
import org.junit.Test

class FinanceCategoriesTest {
    @Test fun defaultChoicesRemainOrderedAndSystemEntriesStayProtected() {
        assertEquals("خوراک", FinanceCategories.expenseDefaults.first())
        assertTrue(FinanceCategories.isProtected(FinanceCategories.EXPENSE, "سایر"))
        assertTrue(FinanceCategories.isProtected(FinanceCategories.INCOME, "حقوق"))
        assertTrue(FinanceCategories.isProtected(FinanceCategories.INCOME, "درآمد"))
        assertFalse(FinanceCategories.isProtected(FinanceCategories.INCOME, "هدیه"))
    }

    @Test fun customChoicesCanBeCreatedRenamedAndRemovedWithoutRemovingDefaults() {
        val kind = FinanceCategories.EXPENSE
        val before = FinanceCategories.expense.toList()
        val created = "دسته تست واحد 404"
        val changed = "دسته تست واحد 404 ویرایش"
        try {
            assertNull(FinanceCategories.add(kind, created))
            assertNotNull(FinanceCategories.add(kind, created))
            assertNull(FinanceCategories.rename(kind, created, changed))
            assertTrue(changed in FinanceCategories.expense)
            assertFalse(created in FinanceCategories.expense)
            assertNull(FinanceCategories.delete(kind, changed))
            assertFalse(changed in FinanceCategories.expense)
        } finally {
            if (created in FinanceCategories.expense) FinanceCategories.delete(kind, created)
            if (changed in FinanceCategories.expense) FinanceCategories.delete(kind, changed)
        }
        assertEquals(before, FinanceCategories.expense)
    }

    @Test fun incomeSalaryMarkerCannotBeRenamedOrRemoved() {
        assertNotNull(FinanceCategories.validation(
            FinanceCategories.INCOME, "حق‌الزحمه", "حقوق"
        ))
        assertNotNull(FinanceCategories.delete(FinanceCategories.INCOME, "حقوق"))
    }
}
