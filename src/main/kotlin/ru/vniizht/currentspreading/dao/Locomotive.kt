package ru.vniizht.currentspreading.dao

import com.vladmihalcea.hibernate.type.json.JsonBinaryType
import com.vladmihalcea.hibernate.type.json.JsonStringType
import org.hibernate.annotations.Type
import org.hibernate.annotations.TypeDef
import org.hibernate.annotations.TypeDefs
import ru.vniizht.currentspreading.dao.enums.LocomotiveCurrent
import ru.vniizht.currentspreading.dao.enums.LocomotiveType
import ru.vniizht.asuterkortes.dao.model.jsonb.BrakingCharacteristics
import ru.vniizht.currentspreading.dao.jsonb.ElectricalPosition
import ru.vniizht.asuterkortes.dao.model.jsonb.LocomotiveResistanceToMotion
import ru.vniizht.asuterkortes.dao.model.jsonb.MotorThermalCharacteristics
import java.time.LocalDateTime
import javax.persistence.*


@Entity
@Table(name = "asu_ter_k_main_locomotive")
@TypeDefs(
    TypeDef(name = "json", typeClass = JsonStringType::class),
    TypeDef(name = "jsonb", typeClass = JsonBinaryType::class)
)
class Locomotive(

    @field:Id
    @field:Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @field:Column(name = "active", nullable = false)
    var active: Boolean = true,

    @field:Column(name = "change_time", nullable = false)
    var changeTime: LocalDateTime = LocalDateTime.now(),

    @field:Column(name = "name", nullable = false)
    var name: String,

    @field:Column(name = "current", nullable = false)
    @field:Enumerated(EnumType.STRING)
    val current: LocomotiveCurrent,

    @field:Column(name = "type", nullable = false)
    @field:Enumerated(EnumType.STRING)
    val type: LocomotiveType,

    @field:Column(name = "power", nullable = true)
    val power: Double,

    @field:Column(name = "weight", nullable = false)
    val weight: Double,

    @field:Column(name = "length", nullable = false)
    val length: Double,

    @field:Column(name = "max_speed", nullable = false)
    val maxSpeed: Int,

    @field:Column(name = "motor_type", nullable = true)
    val motorType: String? = null,

    @field:Column(name = "power_self_consumption", nullable = true)
    val powerSelfConsumption: Double? = null,

    @field:Column(name = "amperage_self_consumption", nullable = true)
    val amperageSelfConsumption: Double? = null,

    @field:Type(type = "jsonb")
    @field:Column(name = "motor_thermal_characteristics", nullable = true, columnDefinition = "jsonb")
    var motorThermalCharacteristics: MotorThermalCharacteristics = MotorThermalCharacteristics(),

    @field:Type(type = "jsonb")
    @field:Column(name = "resistance_to_motion", nullable = false, columnDefinition = "jsonb")
    val resistanceToMotion: LocomotiveResistanceToMotion,

    @field:Type(type = "jsonb")
    @field:Column(name = "electrical_characteristics", nullable = false, columnDefinition = "jsonb")
    val electricalCharacteristics: List<ElectricalPosition>,

    @field:Type(type = "jsonb")
    @field:Column(name = "braking_characteristics", nullable = true, columnDefinition = "jsonb")
    val brakingCharacteristics: BrakingCharacteristics? = null

)