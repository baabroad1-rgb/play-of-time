package online.playoftime.use_case

import android.content.Context

class ContextUseCase(
    val context: Context
) {
    operator fun invoke(): Context {
        return context
    }
}