package ru.vniizht.currentspreading.dao

import javax.persistence.*

@Entity
@Table(name = "asu_ter_u_user")
class User(
    @Id
    @Column(name = "id", nullable = false)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column
    val login: String,

    @Column
    val password: String,

    @Column(name = "last_name")
    var lastName: String,

    @Column(name = "first_name")
    var firstName: String,

    @Column(name = "middle_name")
    var middleName: String,

    @Column(name = "organisation")
    var organization: String
)