package com.pulsepointlabs.elizabethlive.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElizabethDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoPreservesCompletedTrips() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO trips (
                    id, startedAtMillis, endedAtMillis, durationSeconds, distanceKm,
                    averageSpeedKph, maximumSpeedKph, averageRpm, maximumRpm,
                    maximumBoostPsi, minimumCoolantC, maximumCoolantC, minimumIntakeC,
                    maximumIntakeC, averageThrottle, minimumFuelTrim, maximumFuelTrim,
                    minimumVoltage, fuelUsedLiters
                ) VALUES (
                    42, 1000, 61000, 60, 1.5,
                    50.0, 70.0, 1550.0, 2400.0,
                    5.2, 80.0, 92.0, 20.0,
                    38.0, 22.0, -3.0, 4.0,
                    13.8, 0.12
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            ElizabethDatabase.MIGRATION_1_2,
        )
        migrated.query(
            "SELECT id, status, isAutomatic, wasRecovered, fuelDataSource, distanceKm FROM trips WHERE id = 42"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals(42L, cursor.getLong(0))
            assertEquals("COMPLETED", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0, cursor.getInt(3))
            assertEquals("UNAVAILABLE", cursor.getString(4))
            assertEquals(1.5, cursor.getDouble(5), 0.0001)
            assertFalse(cursor.moveToNext())
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DATABASE = "migration-1-2"
    }
}
