/**
 * openCloud Android client application
 *
 * Copyright (C) 2026 ownCloud GmbH.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package eu.opencloud.android.workers

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadProgressCalculatorTest {
    @Test
    fun `percent returns zero when total is unknown or zero`() {
        assertEquals(0, UploadProgressCalculator.percent(transferred = 25, total = -1))
        assertEquals(0, UploadProgressCalculator.percent(transferred = 25, total = 0))
    }

    @Test
    fun `percent returns zero when transferred is zero or negative`() {
        assertEquals(0, UploadProgressCalculator.percent(transferred = 0, total = 100))
        assertEquals(0, UploadProgressCalculator.percent(transferred = -1, total = 100))
    }

    @Test
    fun `percent calculates normal progress`() {
        assertEquals(25, UploadProgressCalculator.percent(transferred = 25, total = 100))
    }

    @Test
    fun `percent clamps over complete progress`() {
        assertEquals(100, UploadProgressCalculator.percent(transferred = 125, total = 100))
    }
}
