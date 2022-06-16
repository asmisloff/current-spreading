package ru.vniizht.currentspreading.service

import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import ru.vniizht.asuterkortes.dto.InstantCircuitACSolutionDataEntry
import ru.vniizht.currentspreading.core.acnew.InstantCircuitAC
import ru.vniizht.currentspreading.core.dcnew.InstantCircuitDC
import ru.vniizht.currentspreading.dao.TractiveCalculate
import ru.vniizht.currentspreading.dto.TractionCountGraphicDto

@Component
@SessionScope
class SessionDataStorage {

    var tcData: List<TractionCountGraphicDto>? = null
    var icDc: InstantCircuitDC? = null
    var icAc: InstantCircuitAC? = null

}