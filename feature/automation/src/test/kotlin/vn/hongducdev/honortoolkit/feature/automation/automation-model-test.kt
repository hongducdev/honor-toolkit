package vn.hongducdev.honortoolkit.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationModelTest {

    @Test
    fun batteryFiresOnlyWhenTheLevelCrossesTheThresholdDownwards() {
        val trigger = Trigger.BatteryLevel(percent = 20, dropping = true)

        assertTrue(trigger.matches(TriggerEvent.BatteryChanged(21, 20)))
        assertTrue(trigger.matches(TriggerEvent.BatteryChanged(25, 19)))
        assertFalse(trigger.matches(TriggerEvent.BatteryChanged(19, 18)))
        assertFalse(trigger.matches(TriggerEvent.BatteryChanged(20, 21)))
    }

    @Test
    fun batteryRisingFiresWhenChargingCrossesUpwards() {
        val trigger = Trigger.BatteryLevel(percent = 80, dropping = false)

        assertTrue(trigger.matches(TriggerEvent.BatteryChanged(79, 80)))
        assertTrue(trigger.matches(TriggerEvent.BatteryChanged(79, 95)))
        assertFalse(trigger.matches(TriggerEvent.BatteryChanged(85, 84)))
    }

    @Test
    fun chargerAndScreenMatchOnlyTheirOwnState() {
        assertTrue(Trigger.Charger(true).matches(TriggerEvent.ChargerChanged(true)))
        assertFalse(Trigger.Charger(true).matches(TriggerEvent.ChargerChanged(false)))
        assertTrue(Trigger.ScreenOff.matches(TriggerEvent.ScreenChanged(false)))
        assertFalse(Trigger.ScreenOff.matches(TriggerEvent.ScreenChanged(true)))
    }

    @Test
    fun wifiWithoutSsidMatchesAnyNetworkOfThatState() {
        val connected = Trigger.Wifi(connected = true)

        assertTrue(connected.matches(TriggerEvent.WifiChanged(true, "Nha")))
        assertTrue(connected.matches(TriggerEvent.WifiChanged(true, null)))
        assertFalse(connected.matches(TriggerEvent.WifiChanged(false, "Nha")))
    }

    @Test
    fun wifiWithSsidIgnoresOtherNetworksAndUnknownNames() {
        val trigger = Trigger.Wifi(connected = true, ssid = "Nha")

        assertTrue(trigger.matches(TriggerEvent.WifiChanged(true, "Nha")))
        assertTrue(trigger.matches(TriggerEvent.WifiChanged(true, "nha")))
        assertFalse(trigger.matches(TriggerEvent.WifiChanged(true, "Cong ty")))
        assertFalse(trigger.matches(TriggerEvent.WifiChanged(true, null)))
    }

    @Test
    fun bluetoothMatchesTheAddressNotTheName() {
        val trigger = Trigger.Bluetooth("AA:BB:CC:DD:EE:FF", "Tai nghe", connected = true)

        assertTrue(trigger.matches(TriggerEvent.BluetoothChanged("aa:bb:cc:dd:ee:ff", "Tên khác", true)))
        assertFalse(trigger.matches(TriggerEvent.BluetoothChanged("AA:BB:CC:DD:EE:00", "Tai nghe", true)))
        assertFalse(trigger.matches(TriggerEvent.BluetoothChanged("AA:BB:CC:DD:EE:FF", "Tai nghe", false)))
    }

    @Test
    fun appAndNotificationTriggersUseThePackageAndTheKeyword() {
        assertTrue(Trigger.AppOpened("com.android.chrome").matches(TriggerEvent.AppChanged("com.android.chrome", true)))
        assertFalse(Trigger.AppOpened("com.android.chrome").matches(TriggerEvent.AppChanged("com.android.chrome", false)))
        assertTrue(Trigger.AppClosed("com.android.chrome").matches(TriggerEvent.AppChanged("com.android.chrome", false)))

        val notification = Trigger.Notification("com.android.mms", "OTP")
        assertTrue(notification.matches(TriggerEvent.NotificationPosted("com.android.mms", "Mã OTP: 123456")))
        assertFalse(notification.matches(TriggerEvent.NotificationPosted("com.android.mms", "Tin nhắn mới")))
        assertFalse(notification.matches(TriggerEvent.NotificationPosted("com.android.mms", null)))

        val anyNotification = Trigger.Notification("com.android.mms", null)
        assertTrue(anyNotification.matches(TriggerEvent.NotificationPosted("com.android.mms", null)))
    }

    @Test
    fun timeAndLocationMatchOnTheirState() {
        assertTrue(Trigger.TimeOfDay(7, 30).matches(TriggerEvent.TimeReached(7, 30, "r")))
        assertFalse(Trigger.TimeOfDay(7, 30).matches(TriggerEvent.TimeReached(7, 31, "r")))
        assertTrue(
            Trigger.LocationRegion(10.0, 106.0, 100f, entering = true)
                .matches(TriggerEvent.LocationChanged(true, "r")),
        )
        assertFalse(
            Trigger.LocationRegion(10.0, 106.0, 100f, entering = true)
                .matches(TriggerEvent.LocationChanged(false, "r")),
        )
    }

    @Test
    fun summariesAreNotBlankAndCarryTheParameters() {
        assertTrue(Trigger.BatteryLevel(20, true).summary().contains("20"))
        assertTrue(Trigger.Wifi(true, "Nha").summary().contains("Nha"))
        assertTrue(Action.Shell("id").summary().contains("shell"))
        assertTrue(Action.TuneFeature("GentleNotifications", true).summary().contains("GentleNotifications"))
    }

    @Test
    fun aCompleteRuleHasNoProblems() {
        val rule = AutomationRule(
            id = "r",
            name = "Cắm sạc",
            enabled = true,
            trigger = Trigger.Charger(true),
            actions = listOf(Action.WriteSetting(SettingScope.Secure, "hnnotification_classification_state", "0")),
        )

        assertEquals(emptyList<String>(), rule.problems())
        assertTrue(rule.isValid)
    }

    @Test
    fun validationCatchesTheObviousMistakes() {
        val bad = AutomationRule(
            id = "r",
            name = " ",
            enabled = true,
            trigger = Trigger.TimeOfDay(25, 61),
            actions = listOf(Action.Shell(" "), Action.LaunchApp("not a package")),
        )

        val problems = bad.problems()

        assertEquals(5, problems.size)
        assertFalse(bad.isValid)
    }

    @Test
    fun aRuleWithoutActionsIsInvalid() {
        val rule = AutomationRule("r", "Rỗng", true, Trigger.ScreenOn, emptyList())

        assertEquals(listOf("Quy tắc chưa có hành động"), rule.problems())
    }
}
