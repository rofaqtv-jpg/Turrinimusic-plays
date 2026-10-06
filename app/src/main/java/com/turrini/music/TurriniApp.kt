package com.turrini.music

import android.app.Application
import androidx.room.Room
import com.turrini.music.data.AppDatabase

class TurriniApp : Application() {
    lateinit var database: AppDatabase
    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "turrini-db").build()
    }
}
