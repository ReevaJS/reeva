package me.mattco.reeva.compiler

import me.mattco.reeva.core.environment.EnvRecord
import me.mattco.reeva.core.environment.GlobalEnvRecord
import me.mattco.reeva.utils.Errors

/**
 * Contains helper methods for use in the Compiler
 */
object CompilerRuntime {
    @JvmStatic
    fun verifyValidGlobalDeclarations(
        env: GlobalEnvRecord,
        varNames: Array<String>,
        lexNames: Array<String>,
        funcDeclNames: Array<String>,
        varDeclNames: Array<String>,
    ) {
        lexNames.forEach {
            if (env.hasVarDeclaration(it))
                Errors.TODO("GlobalEnv has clashing lex-var decl $it").throwSyntaxError()
            if (env.hasLexicalDeclaration(it))
                Errors.TODO("GlobalEnv has clashing lex decl $it").throwSyntaxError()
        }
        varNames.forEach {
            if (env.hasLexicalDeclaration(it)) {
                Errors.TODO("GlobalEnv has clashing var-lex decl $it").throwSyntaxError()
            }
        }
        funcDeclNames.forEach {
            if (!env.canDeclareGlobalFunction(it))
                Errors.TODO("GlobalEnv has clashing func decl $it").throwSyntaxError()
        }

    }
}
