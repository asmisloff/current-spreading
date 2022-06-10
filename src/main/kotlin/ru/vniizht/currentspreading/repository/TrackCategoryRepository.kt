package ru.vniizht.currentspreading.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.vniizht.currentspreading.dao.TrackCategory

interface TrackCategoryRepository : JpaRepository<TrackCategory, Long>