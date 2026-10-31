package vn.hongducdev.honortoolkit.feature.automation

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSettingsPolicyTest {

    @Test
    fun aCatalogueKeyNeedsNoAdvancedFlag() {
        val action = Action.WriteSetting(SettingScope.Secure, "hnnotification_classification_state", "0")

        assertTrue(AutomationSettingsPolicy.isCatalogueKey(action.scope, action.key))
        assertNull(AutomationSettingsPolicy.refusal(action))
    }

    @Test
    fun aKeyOutsideTheCatalogueNeedsAdvanced() {
        val action = Action.WriteSetting(SettingScope.Global, "some_vendor_key", "1")

        assertNotNull(AutomationSettingsPolicy.refusal(action))
        assertNull(AutomationSettingsPolicy.refusal(action.copy(advanced = true)))
    }

    @Test
    fun dangerousKeysStayBlockedEvenInAdvancedMode() {
        val dangerous = listOf(
            "adb_enabled",
            "adb_wifi_enabled",
            "enabled_accessibility_services",
            "enabled_notification_listeners",
            "default_input_method",
            "package_verifier_enable",
            "install_non_market_apps",
            "device_provisioned",
            "user_setup_complete",
            "development_settings_enabled",
        )

        dangerous.forEach { key ->
            val action = Action.WriteSetting(SettingScope.Global, key, "1", advanced = true)
            assertNotNull("$key phải bị chặn", AutomationSettingsPolicy.refusal(action))
        }
    }

    @Test
    fun aBlankKeyIsRefused() {
        assertNotNull(AutomationSettingsPolicy.refusal(Action.WriteSetting(SettingScope.Secure, " ", "1", advanced = true)))
    }
}
