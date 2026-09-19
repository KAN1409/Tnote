package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun followUpDelay_isOneDay() {
    assertEquals(86_400_000L, 24 * 60 * 60 * 1000L)
  }
}
