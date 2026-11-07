package com.englishreader.app.ui.components

import androidx.annotation.StringRes

/** One-off message for a snackbar; [arg] is formatted into the string if present. */
data class UiMessage(@StringRes val res: Int, val arg: Any? = null)

fun Throwable.readable(): String = when (this) {
    is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException -> "no internet connection"
    else -> message ?: javaClass.simpleName
}
