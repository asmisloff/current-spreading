package ru.vniizht.currentspreading.controller

import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class UserController {

    @RequestMapping("/login")
    fun login(@RequestParam(required = false) error: String?, model: Model): String {
        val auth = SecurityContextHolder.getContext().authentication
        if (auth.principal == "anonymousUser") {
            if (error != null) {
                model.addAttribute("error", "Неверная комбинация имени пользователя и пароля.")
            }
            return "login"
        }
        return "index"
    }

}