package ru.vniizht.currentspreading.dao.jsonb

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class ElectricalPosition(
        var name: String,
        var characteristics: List<ElectricalCharacteristic>
)

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = DirectCharacteristic::class, name = "DirectCharacteristic"),
        JsonSubTypes.Type(value = AlternateCharacteristic::class, name = "AlternateCharacteristic")
)
@JsonInclude(JsonInclude.Include.NON_NULL)
sealed class ElectricalCharacteristic {
    abstract var speed: Double?
    abstract var force: Double?
    abstract var motorAmperage: Double?
    abstract var activeCurrentAmperage: Double?
    abstract var efficiency: Double?
}

data class DirectCharacteristic(
    override var speed: Double?,
    override var force: Double?,
    override var motorAmperage: Double?,
    override var activeCurrentAmperage: Double?,
    override var efficiency: Double? = null
) : ElectricalCharacteristic()

data class AlternateCharacteristic(
    override var speed: Double?,
    override var force: Double?,
    override var motorAmperage: Double?,
    var commutateCurrentAmperage: Double?,
    override var activeCurrentAmperage: Double?,
    override var efficiency: Double? = null,
    var powerFactor: Double? = null
) : ElectricalCharacteristic()
