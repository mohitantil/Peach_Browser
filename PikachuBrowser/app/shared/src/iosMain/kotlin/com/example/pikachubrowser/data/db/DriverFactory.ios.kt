package com.example.pikachubrowser.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.pikachubrowser.db.PikachuDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(PikachuDatabase.Schema, "pikachu.db")
    }
}
