@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.funfix.tasks.kotlin

public expect open class TaskCancellationException(message: String?): kotlin.Exception {
    public constructor()
}
