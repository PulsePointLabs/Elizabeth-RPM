package com.pulsepointlabs.elizabethlive.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.pulsepointlabs.elizabethlive.SavedTrip
import com.pulsepointlabs.elizabethlive.SavedTripSummary
import com.pulsepointlabs.elizabethlive.TelemetrySample
import com.pulsepointlabs.elizabethlive.TripEvent
import com.pulsepointlabs.elizabethlive.TripSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationSeconds: Long,
    val distanceKm: Double,
    val averageSpeedKph: Double,
    val maximumSpeedKph: Double,
    val averageRpm: Double,
    val maximumRpm: Double,
    val maximumBoostPsi: Double,
    val minimumCoolantC: Double?,
    val maximumCoolantC: Double?,
    val minimumIntakeC: Double?,
    val maximumIntakeC: Double?,
    val averageThrottle: Double,
    val minimumFuelTrim: Double?,
    val maximumFuelTrim: Double?,
    val minimumVoltage: Double?,
    val fuelUsedLiters: Double,
)

@Entity(
    tableName = "trip_samples",
    primaryKeys = ["tripId", "timestampMillis"],
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripSampleEntity(
    val tripId: Long,
    val timestampMillis: Long,
    val rpm: Double?,
    val speedKph: Double?,
    val boostPsi: Double?,
    val throttlePercent: Double?,
    val coolantC: Double?,
    val intakeC: Double?,
    val shortFuelTrim: Double?,
    val longFuelTrim: Double?,
    val voltage: Double?,
    val engineLoad: Double?,
    val timingAdvance: Double?,
    val fuelRateLitersPerHour: Double?,
    val massAirFlowGramsPerSecond: Double?,
    val commandedEquivalenceRatio: Double?,
    val fuelRateEstimated: Boolean,
)

@Entity(
    tableName = "trip_events",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class TripEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val timestampMillis: Long,
    val label: String,
    val detail: String,
)

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY startedAtMillis DESC")
    fun observeTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTrip(tripId: Long): TripEntity?

    @Query("SELECT * FROM trip_samples WHERE tripId = :tripId ORDER BY timestampMillis")
    suspend fun getSamples(tripId: Long): List<TripSampleEntity>

    @Query("SELECT * FROM trip_events WHERE tripId = :tripId ORDER BY timestampMillis")
    suspend fun getEvents(tripId: Long): List<TripEventEntity>

    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSamples(samples: List<TripSampleEntity>)

    @Insert
    suspend fun insertEvents(events: List<TripEventEntity>)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTrip(tripId: Long)
}

@Database(
    entities = [TripEntity::class, TripSampleEntity::class, TripEventEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class ElizabethDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile private var instance: ElizabethDatabase? = null

        fun get(context: Context): ElizabethDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ElizabethDatabase::class.java,
                    "elizabeth-live.db",
                ).build().also { instance = it }
            }
    }
}

class TripRepository(private val database: ElizabethDatabase) {
    private val dao = database.tripDao()

    val trips: Flow<List<SavedTripSummary>> = dao.observeTrips().map { rows ->
        rows.map(TripEntity::toSavedSummary)
    }

    suspend fun save(
        summary: TripSummary,
        endedAtMillis: Long,
        samples: List<TelemetrySample>,
    ): Long = database.withTransaction {
        val startedAt = summary.startedAtMillis ?: samples.firstOrNull()?.timestampMillis
            ?: endedAtMillis
        val tripId = dao.insertTrip(
            TripEntity(
                startedAtMillis = startedAt,
                endedAtMillis = endedAtMillis,
                durationSeconds = summary.durationSeconds,
                distanceKm = summary.distanceKm,
                averageSpeedKph = summary.averageSpeedKph,
                maximumSpeedKph = summary.maximumSpeedKph,
                averageRpm = summary.averageRpm,
                maximumRpm = summary.maximumRpm,
                maximumBoostPsi = summary.maximumBoostPsi,
                minimumCoolantC = summary.coolantRangeC.nullableMinimum(),
                maximumCoolantC = summary.coolantRangeC.nullableMaximum(),
                minimumIntakeC = summary.intakeRangeC.nullableMinimum(),
                maximumIntakeC = summary.intakeRangeC.nullableMaximum(),
                averageThrottle = summary.averageThrottle,
                minimumFuelTrim = summary.fuelTrimRange.nullableMinimum(),
                maximumFuelTrim = summary.fuelTrimRange.nullableMaximum(),
                minimumVoltage = summary.minimumVoltage.takeUnless { it == 0.0 },
                fuelUsedLiters = summary.fuelUsedLiters,
            )
        )
        dao.insertSamples(samples.map { it.toEntity(tripId) })
        dao.insertEvents(summary.events.map { it.toEntity(tripId) })
        tripId
    }

    suspend fun load(tripId: Long): SavedTrip? = database.withTransaction {
        val trip = dao.getTrip(tripId) ?: return@withTransaction null
        SavedTrip(
            id = trip.id,
            startedAtMillis = trip.startedAtMillis,
            endedAtMillis = trip.endedAtMillis,
            summary = trip.toSummary(),
            samples = dao.getSamples(tripId).map(TripSampleEntity::toSample),
            events = dao.getEvents(tripId).map(TripEventEntity::toEvent),
        )
    }

    suspend fun delete(tripId: Long) = dao.deleteTrip(tripId)
}

private fun TripEntity.toSavedSummary() = SavedTripSummary(
    id = id,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    summary = toSummary(),
)

private fun TripEntity.toSummary() = TripSummary(
    startedAtMillis = startedAtMillis,
    durationSeconds = durationSeconds,
    distanceKm = distanceKm,
    averageSpeedKph = averageSpeedKph,
    maximumSpeedKph = maximumSpeedKph,
    averageRpm = averageRpm,
    maximumRpm = maximumRpm,
    maximumBoostPsi = maximumBoostPsi,
    coolantRangeC = nullableRange(minimumCoolantC, maximumCoolantC),
    intakeRangeC = nullableRange(minimumIntakeC, maximumIntakeC),
    averageThrottle = averageThrottle,
    fuelTrimRange = nullableRange(minimumFuelTrim, maximumFuelTrim),
    minimumVoltage = minimumVoltage ?: 0.0,
    fuelUsedLiters = fuelUsedLiters,
)

private fun TelemetrySample.toEntity(tripId: Long) = TripSampleEntity(
    tripId = tripId,
    timestampMillis = timestampMillis,
    rpm = rpm,
    speedKph = speedKph,
    boostPsi = boostPsi,
    throttlePercent = throttlePercent,
    coolantC = coolantC,
    intakeC = intakeC,
    shortFuelTrim = shortFuelTrim,
    longFuelTrim = longFuelTrim,
    voltage = voltage,
    engineLoad = engineLoad,
    timingAdvance = timingAdvance,
    fuelRateLitersPerHour = fuelRateLitersPerHour,
    massAirFlowGramsPerSecond = massAirFlowGramsPerSecond,
    commandedEquivalenceRatio = commandedEquivalenceRatio,
    fuelRateEstimated = fuelRateEstimated,
)

private fun TripSampleEntity.toSample() = TelemetrySample(
    timestampMillis = timestampMillis,
    rpm = rpm,
    speedKph = speedKph,
    boostPsi = boostPsi,
    throttlePercent = throttlePercent,
    coolantC = coolantC,
    intakeC = intakeC,
    shortFuelTrim = shortFuelTrim,
    longFuelTrim = longFuelTrim,
    voltage = voltage,
    engineLoad = engineLoad,
    timingAdvance = timingAdvance,
    fuelRateLitersPerHour = fuelRateLitersPerHour,
    massAirFlowGramsPerSecond = massAirFlowGramsPerSecond,
    commandedEquivalenceRatio = commandedEquivalenceRatio,
    fuelRateEstimated = fuelRateEstimated,
)

private fun TripEvent.toEntity(tripId: Long) = TripEventEntity(
    tripId = tripId,
    timestampMillis = timestampMillis,
    label = label,
    detail = detail,
)

private fun TripEventEntity.toEvent() = TripEvent(timestampMillis, label, detail)

private fun ClosedFloatingPointRange<Double>.nullableMinimum(): Double? =
    start.takeUnless { start == 0.0 && endInclusive == 0.0 }

private fun ClosedFloatingPointRange<Double>.nullableMaximum(): Double? =
    endInclusive.takeUnless { start == 0.0 && endInclusive == 0.0 }

private fun nullableRange(minimum: Double?, maximum: Double?): ClosedFloatingPointRange<Double> =
    if (minimum == null || maximum == null) 0.0..0.0 else minimum..maximum
