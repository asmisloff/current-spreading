package ru.vniizht.asuterkortes.dao.model.jsonb

import ru.vniizht.currentspreading.dao.jsonb.ElectricalCharacteristic


data class BrakingCharacteristics(
    var limit: List<ElectricalCharacteristic>,
    var max: List<ElectricalCharacteristic>

)