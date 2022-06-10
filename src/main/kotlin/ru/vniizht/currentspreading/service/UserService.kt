package ru.vniizht.currentspreading.service

import javassist.NotFoundException
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.stereotype.Service
import ru.vniizht.currentspreading.dao.CSUserDetails
import ru.vniizht.currentspreading.repository.UserRepository

@Service
class UserService(val repository: UserRepository) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val user = repository.findByLogin(username) ?: throw NotFoundException("Пользователь $username не найден")
//        return User.builder()
//            .username(user.login)
//            .password(user.password)
//            .roles("")
//            .build()
        return CSUserDetails(user.login, user.password, user.firstName, user.lastName, user.middleName)
    }

}