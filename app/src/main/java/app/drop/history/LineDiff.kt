package app.drop.history

data class DiffLine(val kind: Kind, val text: String) {
    enum class Kind { Same, Removed, Added }
}

/** Line-by-line differences between an earlier version and the current text. */
object LineDiff {
    /** Above this many comparisons the diff is skipped; the version can still be previewed. */
    const val MAX_CELLS = 4_000_000L

    /** Null when the texts are too long to compare line by line. */
    fun of(old: String, new: String): List<DiffLine>? {
        val a = old.split('\n')
        val b = new.split('\n')
        // Lines shared at the start and end need no comparing.
        var head = 0
        while (head < a.size && head < b.size && a[head] == b[head]) head++
        var tail = 0
        while (tail < a.size - head && tail < b.size - head && a[a.size - 1 - tail] == b[b.size - 1 - tail]) tail++
        val am = a.subList(head, a.size - tail)
        val bm = b.subList(head, b.size - tail)
        if (am.size.toLong() * bm.size.toLong() > MAX_CELLS) return null
        val out = ArrayList<DiffLine>(a.size + b.size)
        a.subList(0, head).forEach { out += DiffLine(DiffLine.Kind.Same, it) }
        val n = am.size
        val m = bm.size
        // Longest common subsequence, filled from the end.
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (am[i] == bm[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        var i = 0
        var j = 0
        while (i < n || j < m) {
            when {
                i < n && j < m && am[i] == bm[j] -> {
                    out += DiffLine(DiffLine.Kind.Same, am[i])
                    i++
                    j++
                }
                i >= n || (j != m && lcs[i + 1][j] < lcs[i][j + 1]) -> out += DiffLine(DiffLine.Kind.Added, bm[j++])
                else -> out += DiffLine(DiffLine.Kind.Removed, am[i++])
            }
        }
        a.subList(a.size - tail, a.size).forEach { out += DiffLine(DiffLine.Kind.Same, it) }
        return out
    }
}
