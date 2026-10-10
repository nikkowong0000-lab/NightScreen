package com.yourcompany.screenlight

import android.app.Application
import android.content.Context

class NightScreenApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }
}
