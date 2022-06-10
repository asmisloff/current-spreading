package ru.vniizht.asuterkortes.dao.model.jsonb

data class MotorThermalCharacteristics(
    var overheatTolerance: Double = 100.0,
    var thermalTimeConstant: Double = 20.0,
    var characteristics: List<MotorThermalCharacteristic> = emptyList()
)

data class MotorThermalCharacteristic(
    var motorAmperage: Double?,
    var balancingOverheat: Double?,
)
