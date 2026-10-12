package com.example.peachbrowser.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.peachbrowser.db.PeachDatabase

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(PeachDatabase.Schema, "peach.db")
    }
}
