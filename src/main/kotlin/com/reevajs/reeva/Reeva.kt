package com.reevajs.reeva

import com.reevajs.reeva.core.lifecycle.*
import com.reevajs.reeva.core.realm.Realm
import com.reevajs.reeva.parsing.ParsingError
import com.reevajs.reeva.utils.Result

object Reeva {
    @JvmStatic
    fun setup() {
        Realm.setupSymbols()
    }

    fun compile(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, Executable> {
        return if (sourceInfo.isModule) {
            compileModule(realm, sourceInfo).cast()
        } else compileScript(realm, sourceInfo).cast()
    }

    fun compileScript(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, Script> {
        return Script.parseScript(realm, sourceInfo)
    }

    fun compileModule(realm: Realm, sourceInfo: SourceInfo): Result<ParsingError, ModuleRecord> {
        return SourceTextModuleRecord.parseModule(realm, sourceInfo)
    }
}
