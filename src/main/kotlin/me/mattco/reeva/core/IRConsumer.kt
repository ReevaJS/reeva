package me.mattco.reeva.core

import me.mattco.reeva.Reeva
import me.mattco.reeva.core.tasks.Task
import me.mattco.reeva.ir.FunctionInfo

interface IRConsumer {
    fun consume(info: FunctionInfo, realm: Realm): Task<Reeva.Result>
}
