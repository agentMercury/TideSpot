package com.tidespot.core.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tidespot.core.ui.strings.EnStrings
import com.tidespot.core.ui.strings.ResStrings
import com.tidespot.core.ui.strings.TideSpotStrings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * OPT-V11-13 英文链路锁：`EnStrings`（纯 Kotlin 英文实现）与 `res/values-en/strings.xml` 逐键相等，
 * 且与 `values/` 的键集**完全对齐**（漏翻/多键都会失败）。
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class EnStringsParityTest {

    private val en = EnStrings
    private val resStrings = ResStrings(
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().resources,
    )

    private fun stringGetters(): List<Method> = TideSpotStrings::class.java.methods.filter {
        it.parameterCount == 0 && it.returnType == String::class.java &&
            it.name.startsWith("get") && it.name != "getClass"
    }

    @Test
    fun enStrings_matchesEnglishResources_keyByKey() {
        val getters = stringGetters()
        assertTrue(getters.size > 50)
        getters.forEach { getter ->
            assertEquals(
                getter.invoke(en),
                getter.invoke(resStrings),
                "键 ${getter.name} 在 EnStrings 与 values-en/strings.xml 不一致",
            )
        }
    }

    @Test
    fun english_copy_isNotChinese() {
        assertTrue(!en.tabWave.any { it.code in 0x4E00..0x9FFF })
        assertTrue(!en.disclaimerScore.any { it.code in 0x4E00..0x9FFF })
        assertEquals("Prime window", en.bandExplosive)
    }
}