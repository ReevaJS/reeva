package com.reevajs.reeva

import com.reevajs.reeva.core.realm.Realm

object Reeva {
    @JvmStatic
    fun setup() {
        Realm.setupSymbols()
    }
}
