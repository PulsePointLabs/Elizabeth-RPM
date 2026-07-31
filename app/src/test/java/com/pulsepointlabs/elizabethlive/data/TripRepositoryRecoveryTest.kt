package com.pulsepointlabs.elizabethlive.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripEvent
import com.pulsepointlabs.elizabethlive.TripSummary
import com.pulsepointlabs.elizabethlive.FuelDataSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TripRepositoryRecoveryTest {
    private lateinit var database: ElizabethDatabase
    private lateinit var repository: TripRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ElizabethDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TripRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun samplesAreWrittenIncrementallyAndRecoveredAfterRepositoryRecreation() = runTest {
        val tripId = repository.beginActive(startedAtMillis = 1_000, automatic = true)
        val firstBatch = listOf(sample(1_000), sample(1_500), sample(2_000))
        repository.flushActive(
            tripId = tripId,
            summary = summary(startedAt = 1_000, duration = 1, distance = 0.02),
            samples = firstBatch,
            events = listOf(TripEvent(2_000, "Connection gap", "ECU temporarily unavailable")),
            lastSampleMillis = 2_000,
            fuelDataSource = "MAF_ESTIMATED",
            reconnectCount = 1,
            graceStartedAtMillis = 2_100,
            recovered = false,
        )

        assertEquals(3, repository.sampleCount(tripId))
        val recreatedRepository = TripRepository(database)
        val recovered = recreatedRepository.loadActive()

        assertNotNull(recovered)
        assertEquals(tripId, recovered!!.trip.id)
        assertTrue(recovered.trip.isAutomatic)
        assertEquals("MAF_ESTIMATED", recovered.trip.fuelDataSource)
        assertEquals(3, recovered.samples.size)
        assertEquals(1, recovered.events.size)
        assertTrue(recovered.summary.isRecording)
    }

    @Test
    fun recoveredActiveTripFinalizesWithoutDiscardingSamples() = runTest {
        val tripId = repository.beginActive(startedAtMillis = 1_000, automatic = true)
        repository.flushActive(
            tripId = tripId,
            summary = summary(1_000, 2, 0.03),
            samples = listOf(sample(1_000), sample(2_000)),
            events = emptyList(),
            lastSampleMillis = 2_000,
            fuelDataSource = "UNAVAILABLE",
            reconnectCount = 2,
            graceStartedAtMillis = 2_000,
            recovered = true,
        )

        repository.finalizeActive(
            tripId = tripId,
            summary = summary(1_000, 2, 0.03),
            endedAtMillis = 2_000,
            lastSampleMillis = 2_000,
            fuelDataSource = "UNAVAILABLE",
            reconnectCount = 2,
            recovered = true,
        )

        assertNull(repository.loadActive())
        val completed = repository.load(tripId)
        assertNotNull(completed)
        assertEquals(2, completed!!.samples.size)
        assertFalse(completed.summary.isRecording)
        assertEquals(FuelDataSource.UNAVAILABLE, completed.fuelDataSource)
        assertTrue(completed.wasRecovered)
        assertEquals(2, completed.reconnectCount)
        val entity = database.tripDao().getTrip(tripId)
        assertEquals("COMPLETED", entity!!.status)
        assertTrue(entity.wasRecovered)
        assertEquals("UNAVAILABLE", entity.fuelDataSource)
    }

    private fun sample(timestamp: Long) = TelemetrySample(
        timestampMillis = timestamp,
        rpm = 800.0,
        speedKph = 35.0,
        boostPsi = -4.0,
        throttlePercent = 18.0,
        coolantC = 88.0,
        intakeC = 32.0,
        shortFuelTrim = 1.5,
        longFuelTrim = -2.0,
        voltage = 14.1,
        engineLoad = 24.0,
        timingAdvance = 12.0,
        fuelRateLitersPerHour = null,
        massAirFlowGramsPerSecond = null,
        commandedEquivalenceRatio = null,
        fuelRateEstimated = false,
    )

    private fun summary(startedAt: Long, duration: Long, distance: Double) = TripSummary(
        isRecording = true,
        startedAtMillis = startedAt,
        durationSeconds = duration,
        distanceKm = distance,
        averageSpeedKph = 35.0,
        maximumSpeedKph = 35.0,
        averageRpm = 800.0,
        maximumRpm = 800.0,
    )
}
