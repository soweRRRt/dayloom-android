package com.sowerrrt.dayloom.core.updates

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<SemVer> {
    val isStable: Boolean get() = preRelease.isEmpty()

    override fun compareTo(other: SemVer): Int {
        compareValues(major, other.major).takeIf { it != 0 }?.let { return it }
        compareValues(minor, other.minor).takeIf { it != 0 }?.let { return it }
        compareValues(patch, other.patch).takeIf { it != 0 }?.let { return it }
        if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
        if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
        for (index in 0 until maxOf(preRelease.size, other.preRelease.size)) {
            val left = preRelease.getOrNull(index) ?: return -1
            val right = other.preRelease.getOrNull(index) ?: return 1
            compareIdentifier(left, right).takeIf { it != 0 }?.let { return it }
        }
        return 0
    }

    override fun toString(): String =
        buildString {
            append("$major.$minor.$patch")
            if (preRelease.isNotEmpty()) append("-${preRelease.joinToString(".")}")
        }

    companion object {
        private val pattern =
            Regex(
                "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z.-]+)?$",
            )

        fun parseOrNull(value: String): SemVer? {
            val match = pattern.matchEntire(value.trim()) ?: return null
            return SemVer(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
                preRelease = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.') ?: emptyList(),
            )
        }

        private fun compareIdentifier(
            left: String,
            right: String,
        ): Int {
            val leftNumber = left.toIntOrNull()
            val rightNumber = right.toIntOrNull()
            return when {
                leftNumber != null && rightNumber != null -> compareValues(leftNumber, rightNumber)
                leftNumber != null -> -1
                rightNumber != null -> 1
                else -> left.compareTo(right)
            }
        }
    }
}
