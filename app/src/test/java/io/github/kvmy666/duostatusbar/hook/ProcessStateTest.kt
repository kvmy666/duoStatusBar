package io.github.kvmy666.duostatusbar.hook

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The per-process install claim MainHook owns: exactly one winner, and a failed install releases it. */
class ProcessStateTest {

    @Before
    fun setUp() {
        ProcessState.resetInstall()
    }

    @After
    fun tearDown() {
        ProcessState.resetInstall()
    }

    @Test
    fun `the first claim wins and the second load is a duplicate`() {
        assertTrue(ProcessState.claimInstall())
        assertFalse(ProcessState.claimInstall())
    }

    @Test
    fun `a released claim allows a later load to retry`() {
        assertTrue(ProcessState.claimInstall())
        ProcessState.resetInstall()
        assertTrue(ProcessState.claimInstall())
    }
}
