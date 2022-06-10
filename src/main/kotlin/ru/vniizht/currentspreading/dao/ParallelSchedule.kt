package ru.vniizht.asuterkortes.dao.model

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.enums.CapacityType
import ru.vniizht.currentspreading.dto.ParallelScheduleResultDto
import ru.vniizht.asuterkortes.dto.ScheduleParametersDto
import ru.vniizht.currentspreading.dao.ElectricalSchema
import ru.vniizht.currentspreading.dao.Track
import java.time.LocalDateTime
import javax.persistence.*


@Entity
@TypeDefs(
        TypeDef(name = "json", typeClass = JsonStringType::class),
        TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
@Table(name = "asu_ter_m_parallel_schedule")
class ParallelSchedule(

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

    @field:Column(name = "type", nullable = false)
        @field:Enumerated(EnumType.STRING)
        var type: CapacityType,

    @field:Type(type = "jsonb")
        @field:Column(name = "schedule_parameters", columnDefinition = "jsonb")
        var scheduleParameters: List<ScheduleParametersDto>,

    @field:Type(type = "jsonb")
        @field:Column(name = "result", columnDefinition = "jsonb")
        var result: ParallelScheduleResultDto?,

    @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "schema_id", referencedColumnName = "id")
        val schema: ElectricalSchema?,

    @field:ManyToOne(fetch = FetchType.LAZY)
        @field:JoinColumn(name = "track_id", referencedColumnName = "id")
        val track: Track?,

    @field:Column(name = "start_time")
        var startTime: Int,

    @field:Column(name = "finish_time")
        var finishTime: Int

) {
        override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as ParallelSchedule

                if (id != other.id) return false

                return true
        }

        override fun hashCode(): Int {
                return id.hashCode()
        }
}
