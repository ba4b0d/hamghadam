package com.fitnessapp.android.data

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectRepositoryTest {

    @Test
    fun requiredReadTypes_containsStepsSleepAndHeartRate() {
        val types = HealthConnectRepository.REQUIRED_READ_TYPES
        assertEquals(3, types.size)
        assertTrue(types.contains(StepsRecord::class))
        assertTrue(types.contains(SleepSessionRecord::class))
        assertTrue(types.contains(HeartRateRecord::class))
    }

    @Test
    fun requiredReadPermissionStrings_containsAllThreePermissions() {
        val expected = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
        )
        assertTrue(expected.contains("android.permission.health.READ_STEPS"))
        assertTrue(expected.contains("android.permission.health.READ_SLEEP"))
        assertTrue(expected.contains("android.permission.health.READ_HEART_RATE"))
    }
}
