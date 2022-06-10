package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.User

interface UserRepository : JpaRepository<User, Long> {
    fun findByLogin(login: String): User?
}