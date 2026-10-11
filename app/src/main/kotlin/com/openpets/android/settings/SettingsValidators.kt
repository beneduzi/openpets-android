package com.openpets.android.settings

import com.openpets.android.protocol.isValidRemoteToken

object SettingsValidators {
    enum class Error { REQUIRED, NOT_A_NUMBER, OUT_OF_RANGE, CONFLICT, TOO_SHORT, TOO_LONG, INVALID_CHARACTERS, INVALID_FORMAT, START_EQUALS_END }
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Invalid(val error: Error) : Result<Nothing>()
    }

    private fun number(raw: String, range: IntRange): Result<Int> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.Invalid(Error.REQUIRED)
        val parsed = value.toIntOrNull() ?: return Result.Invalid(Error.NOT_A_NUMBER)
        return if (parsed in range) Result.Ok(parsed) else Result.Invalid(Error.OUT_OF_RANGE)
    }
    fun port(raw: String) = number(raw, 1..65535)
    fun distinctPorts(enabled: Map<String, Int>): Result<Unit> =
        if (enabled.values.size == enabled.values.toSet().size) Result.Ok(Unit) else Result.Invalid(Error.CONFLICT)

    fun ipcToken(raw: String): Result<String> {
        val value = raw.trim()
        if (value.length < 16) return Result.Invalid(Error.TOO_SHORT)
        if (value.length > 256) return Result.Invalid(Error.TOO_LONG)
        if (value.any { it.isISOControl() }) return Result.Invalid(Error.INVALID_CHARACTERS)
        return Result.Ok(value)
    }
    fun remoteToken(raw: String): Result<String> {
        val value = raw.trim()
        if (value.length < 43) return Result.Invalid(Error.TOO_SHORT)
        if (value.length > 256) return Result.Invalid(Error.TOO_LONG)
        if (!isValidRemoteToken(value)) return Result.Invalid(Error.INVALID_CHARACTERS)
        return Result.Ok(value)
    }

    fun bindAddress(raw: String): Result<String> {
        val value = raw.trim()
        if (value.isEmpty()) return Result.Ok("0.0.0.0")
        if (value.any { it.isWhitespace() } || "://" in value || '/' in value || value.contains('@')) return Result.Invalid(Error.INVALID_FORMAT)
        if ('%' in value) {
            val pieces = value.split('%')
            if (pieces.size != 2 || !pieces[1].matches(Regex("[A-Za-z0-9_.-]{1,32}")) || !validIpv6(pieces[0])) return Result.Invalid(Error.INVALID_FORMAT)
            return Result.Ok(value)
        }
        if (':' in value) return if (validIpv6(value)) Result.Ok(value) else Result.Invalid(Error.INVALID_FORMAT)
        if (value.matches(Regex("[0-9.]+"))) return if (validIpv4(value)) Result.Ok(value) else Result.Invalid(Error.INVALID_FORMAT)
        val hostname = value.removeSuffix(".")
        val valid = hostname.isNotEmpty() && hostname.length <= 253 && hostname.split('.').all {
            it.length in 1..63 && it.first().isLetterOrDigit() && it.last().isLetterOrDigit() && it.all { c -> c.isLetterOrDigit() || c == '-' }
        }
        return if (valid) Result.Ok(value) else Result.Invalid(Error.INVALID_FORMAT)
    }

    private fun validIpv4(value: String): Boolean {
        val octets = value.split('.')
        return octets.size == 4 && octets.all { it.isNotEmpty() && it.all(Char::isDigit) && it.toIntOrNull() in 0..255 }
    }

    private fun validIpv6(value: String): Boolean {
        if (value.isEmpty() || value.countSubstring("::") > 1) return false
        val compressed = "::" in value
        if (value.contains(Regex("(?<!:):::(?!:)"))) return false
        val parts = if (compressed) value.split("::") else listOf(value)
        if (parts.any { it.isNotEmpty() && (it.startsWith(':') || it.endsWith(':')) }) return false
        val tokens = parts.filter { it.isNotEmpty() }.flatMap { it.split(':') }
        var groups = 0
        for ((index, token) in tokens.withIndex()) {
            if ('.' in token) {
                if (index != tokens.lastIndex || (compressed && parts.last().isEmpty()) || !validIpv4(token)) return false
                groups += 2
            } else {
                if (token.length !in 1..4 || !token.all { it in "0123456789abcdefABCDEF" }) return false
                groups++
            }
        }
        return if (compressed) groups <= 7 else groups == 8
    }

    private fun String.countSubstring(substring: String): Int = windowed(substring.length).count { it == substring }

    fun waterWindow(startHour: Int, endHour: Int): Result<Unit> =
        if (startHour == endHour) Result.Invalid(Error.START_EQUALS_END) else Result.Ok(Unit)

    fun waitingDuration(raw: String) = number(raw, 200..10_000)
    fun waterInterval(raw: String) = number(raw, 15..240)
    fun waterHour(raw: String) = number(raw, 0..23)
    fun routineHour(raw: String) = number(raw, 0..23)
    fun routineMinute(raw: String) = number(raw, 0..59)
}
