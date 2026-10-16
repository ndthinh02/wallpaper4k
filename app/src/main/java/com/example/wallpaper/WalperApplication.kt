package com.example.wallpaper

import android.app.Application
import com.example.wallpaper.repository.AppRepository

class WalperApplication : Application() {
    val repository by lazy { AppRepository(this) }
}
