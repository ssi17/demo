package com.example.okishiru.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Setting(
    @PrimaryKey val name: String,
    @ColumnInfo var flag: Int
)