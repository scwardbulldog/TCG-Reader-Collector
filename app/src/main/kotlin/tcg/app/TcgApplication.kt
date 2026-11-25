package tcg.app

import android.app.Application
import tcg.app.storage.AppDatabase

class TcgApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.open(this) }
}
