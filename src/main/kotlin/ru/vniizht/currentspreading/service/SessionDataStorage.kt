package ru.vniizht.currentspreading.service

import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.SessionScope
import ru.vniizht.currentspreading.dao.TractiveCalculate

@Component
@SessionScope
class SessionDataStorage {

    var tc: TractiveCalculate? = null
    var testNumber = 0

}