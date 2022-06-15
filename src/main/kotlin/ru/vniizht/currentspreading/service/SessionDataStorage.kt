package ru.vniizht.currentspreading.service

import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import ru.vniizht.currentspreading.dao.TractiveCalculate
import ru.vniizht.currentspreading.dto.TractionCountGraphicDto

@Component
@SessionScope
class SessionDataStorage {

    var tcData: List<TractionCountGraphicDto>? = null

}