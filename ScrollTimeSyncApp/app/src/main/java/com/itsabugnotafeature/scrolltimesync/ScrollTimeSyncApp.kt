package com.itsabugnotafeature.scrolltimesync

import android.app.Application
import com.itsabugnotafeature.scrolltimesync.sync.SyncScheduler

class ScrollTimeSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedule(this)
    }
}
