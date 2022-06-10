package ru.vniizht.asuterkortes.dao.model

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.enums.CapacityType
import ru.vniizht.asuterkortes.dto.DailyCapacityResultDto
import ru.vniizht.asuterkortes.dto.ScheduleParametersDto
import ru.vniizht.asuterkortes.dto.TrackParameterDto
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.dao.Track
import java.time.LocalDateTime
import javax.persistence.*


@Entity
@TypeDefs(
        TypeDef(name = "json", typeClass = JsonStringType::class),
        TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_m_crossing_capacity")
class CrossingCapacity(

    @field:Id
        @field:Column(name = "id")
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        val id: Long? = null,

    @field:Column(name = "user_login", nullable = true)
        val userLogin: String?,

    @field:Column(name = "active", nullable = false)
        var active: Boolean = true,

    @field:Column(name = "change_time", nullable = false)
        var changeTime: LocalDateTime = LocalDateTime.now(),

    @field:Column(name = "name", nullable = false)
        var name: String,

    @field:Column(name = "description", nullable = false)
        var description: String,

    @field:Column(name = "iterations")
        var iterations: Int,

    @field:Column(name = "station_durability")
        var stationDurability: Double,

    @field:Column(name = "network_durability")
        var networkDurability: Double,

    @field:Column(name = "tools_durability")
        var toolsDurability: Double,

    @field:Column(name = "window_time")
        var windowTime: Int = 0,

    @field:Column(name = "train_interval")
        var trainInterval: Int = 0,

    @field:Column(name = "type", nullable = false)
        @field:Enumerated(EnumType.STRING)
        var type: CapacityType,

    @field:Type(type = "jsonb")
        @field:Column(name = "track_traffic", columnDefinition = "jsonb")
        var trackParameters: List<TrackParameterDto>,

    @field:Type(type = "jsonb")
        @field:Column(name = "schedule_parameters", columnDefinition = "jsonb")
        var scheduleParameters: List<ScheduleParametersDto>,

    @field:Type(type = "jsonb")
        @field:Column(name = "result", columnDefinition = "jsonb")
        var result: DailyCapacityResultDto?,

    @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "schema_id", referencedColumnName = "id")
        val schema: ElectricalSchema?,

    @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "track_id", referencedColumnName = "id")
        val track: Track?

) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as CrossingCapacity

                if (id != other.id) return false

                return true
        }

        override fun hashCode(): Int {
                return id.hashCode()
        }
}
