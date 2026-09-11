package li.songe.morph.playground

private val IntegerGlyphs.magnitude: String get() = digits.trimStart('0').ifEmpty { "0" }

internal fun IntegerGlyphs.compareNumeric(other: IntegerGlyphs): Int {
    val left = magnitude
    val right = other.magnitude
    val leftNegative = negative && left != "0"
    val rightNegative = other.negative && right != "0"
    if (leftNegative != rightNegative) return if (leftNegative) -1 else 1
    val magnitudeOrder = if (left.length != right.length) left.length.compareTo(right.length) else left.compareTo(right)
    return if (leftNegative) -magnitudeOrder else magnitudeOrder
}

/** Produces just the next integer; even very large ranges never allocate a sequence. */
internal fun IntegerGlyphs.stepToward(target: IntegerGlyphs): IntegerGlyphs {
    val direction = compareNumeric(target)
    if (direction == 0) return target
    val absolute = magnitude
    val isNegative = negative && absolute != "0"
    val next = if (absolute == "0") {
        IntegerGlyphs("1", direction > 0)
    } else {
        val grow = (direction < 0) != isNegative
        val chars = absolute.toCharArray()
        var index = chars.lastIndex
        val rollover = if (grow) '9' else '0'
        while (index >= 0 && chars[index] == rollover) {
            chars[index--] = if (grow) '0' else '9'
        }
        val changed = if (index < 0) "1" + chars.concatToString() else {
            chars[index] = (chars[index].code + if (grow) 1 else -1).toChar()
            chars.concatToString().trimStart('0').ifEmpty { "0" }
        }
        IntegerGlyphs(changed, isNegative && changed != "0")
    }
    if (next.compareNumeric(target) == 0) return target
    // Preserve an explicitly padded source during counting, then use the exact target at the end.
    return if (digits.length > 1 && digits.startsWith('0')) next.copy(digits = next.digits.padStart(digits.length, '0')) else next
}
