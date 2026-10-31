package vn.hongducdev.honortoolkit.feature.tune

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuneParsersTest {
    @Test
    fun parsesValuesWithMarkers() {
        val dump = """
            __HT_system/hiboard_switch=0
            __HT_secure/hiboard_switch=0
            __HT_global/private_dns_mode=opportunistic
            unrelated output
        """.trimIndent()

        val values = parseSettingsDump(dump)

        assertEquals("0", values[SettingKey(TuneNamespace.System, "hiboard_switch")])
        assertEquals("0", values[SettingKey(TuneNamespace.Secure, "hiboard_switch")])
        assertEquals("opportunistic", values[SettingKey(TuneNamespace.Global, "private_dns_mode")])
        assertEquals(3, values.size)
    }

    @Test
    fun keepsEqualsSignsInsideTheValue() {
        val dump = "__HT_global/job_scheduler_constants=min_ready_non_active_jobs_count=1,max_non_active_job_batch_delay_ms=0"

        val value = parseSettingsDump(dump)[SettingKey(TuneNamespace.Global, "job_scheduler_constants")]

        assertEquals("min_ready_non_active_jobs_count=1,max_non_active_job_batch_delay_ms=0", value)
    }

    @Test
    fun treatsNullAndEmptyAsMissing() {
        val dump = """
            __HT_global/private_dns_specifier=null
            __HT_system/min_match=
        """.trimIndent()

        val values = parseSettingsDump(dump)

        assertNull(values[SettingKey(TuneNamespace.Global, "private_dns_specifier")])
        assertNull(values[SettingKey(TuneNamespace.System, "min_match")])
    }

    @Test
    fun ignoresLinesWithoutTheMarker() {
        assertEquals(emptyMap<SettingKey, String?>(), parseSettingsDump("system/hiboard_switch=0"))
    }
}
