package io.homeassistant.companion.android.dashboard.search

import java.text.Normalizer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

// The frontend's fuzzy search of its pickers: `multiTermSearch` and `multiTermSortedSearch`
// (frontend@20260624.6 src/resources/fuseMultiTerm.ts) over Fuse.js 7.4.2 as they configure it (case and accents
// ignored, threshold 0.3, location ignored): its index with field-length norms, its Bitap matcher, its scores, and
// its logical `$and`/`$or` queries.

/** A searched field: its [name] and its [weight] among the others. */
data class FuseKey(val name: String, val weight: Double)

/**
 * Port of `Fuse.createIndex`: the [items]' searchable [keys], read by [values] (a field's text by key name, `null`
 * when it has none).
 */
class FuseIndex<T>(val items: List<T>, val keys: List<FuseKey>, values: (T) -> Map<String, String?>) {
    internal val records: List<Array<FuseField?>> = items.map { item ->
        val fields = values(item)
        Array(keys.size) { index ->
            fields[keys[index].name]?.takeIf { it.isNotBlank() }?.let { FuseField(it, fieldNorm(it)) }
        }
    }
}

/** A field's text and its length norm. */
internal class FuseField(val text: String, val norm: Double)

/**
 * Port of `multiTermSearch`: the items matching every space-separated term of [search] in some field, in their
 * order.
 */
fun <T> multiTermSearch(index: FuseIndex<T>, search: String): List<T> {
    val terms = terms(search)
    if (terms.isEmpty()) return index.items
    val minLength = minOf(DEFAULT_MIN_CHAR_LENGTH, terms.minOf { it.length })
    val searchers = terms.map { BitapSearcher(it, minLength) }
    return index.items.filterIndexed { idx, _ ->
        val fields = index.records[idx]
        searchers.all { searcher -> fields.any { field -> field != null && searcher.searchIn(field.text).isMatch } }
    }
}

/**
 * Port of `multiTermSortedSearch`: the items matching every term of [search], the best matches first (by Fuse's
 * score for one term; for several, by the sum of their scores' negative logs).
 */
fun <T> multiTermSortedSearch(index: FuseIndex<T>, search: String): List<T> {
    val terms = terms(search)
    return when (terms.size) {
        0 -> index.items
        1 -> scoredResults(index, terms.single()).sortedWith(compareBy({ it.second }, { it.first })).map {
            index.items[it.first]
        }
        else -> severalTermsSorted(index, terms)
    }
}

/** The items matching every one of [terms], by the sum of their scores' negative logs. */
private fun <T> severalTermsSorted(index: FuseIndex<T>, terms: List<String>): List<T> {
    val hits = mutableMapOf<Int, Int>()
    val scores = mutableMapOf<Int, Double>()
    var termHits = 0
    terms.forEach { term ->
        val results = scoredResults(index, term)
        if (results.isNotEmpty()) termHits++
        results.forEach { (idx, score) ->
            hits[idx] = (hits[idx] ?: 0) + 1
            scores[idx] = (scores[idx] ?: 0.0) - ln(if (score == 0.0) Double.MIN_VALUE else score)
        }
    }
    // Insertion ordered, so the stable sort keeps ties in the first term's order
    return if (termHits != terms.size) {
        emptyList()
    } else {
        hits.filterValues { it == terms.size }.keys.sortedByDescending { scores.getValue(it) }.map { index.items[it] }
    }
}

private fun terms(search: String): List<String> = search.lowercase().split(" ").filter { it.isNotBlank() }

/** Each matching item's index and Fuse score (`computeScoreSingle`), in the items' order. */
private fun <T> scoredResults(index: FuseIndex<T>, term: String): List<Pair<Int, Double>> {
    val searcher = BitapSearcher(term, minOf(term.length, DEFAULT_MIN_CHAR_LENGTH))
    return index.records.mapIndexedNotNull { idx, fields ->
        var total = 1.0
        var matched = false
        fields.forEachIndexed { keyIndex, field ->
            if (field == null) return@forEachIndexed
            val result = searcher.searchIn(field.text)
            if (result.isMatch) {
                matched = true
                val weight = index.keys[keyIndex].weight
                val score = if (result.score == 0.0) EPSILON else result.score
                total *= score.pow(weight * field.norm)
            }
        }
        if (matched) idx to total else null
    }
}

/** Port of Fuse's `norm`: 1 over the square root of the field's number of words, to 3 decimals. */
private fun fieldNorm(text: String): Double {
    var tokens = 1
    var inSpace = false
    text.forEach { char ->
        if (char == ' ') {
            if (!inSpace) tokens++
            inSpace = true
        } else {
            inSpace = false
        }
    }
    return (NORM_SCALE / sqrt(tokens.toDouble())).roundToLong() / NORM_SCALE
}

/** Whether a pattern matched a text, and how well (0 exact, up to the threshold). */
internal data class BitapResult(val isMatch: Boolean, val score: Double)

/** Port of `BitapSearch` with the pickers' options: case and accents ignored, location ignored. */
internal class BitapSearcher(pattern: String, private val minMatchCharLength: Int) {
    private val pattern = normalize(pattern)
    private val chunks: List<Chunk> = chunk(this.pattern).map { (start, text) -> Chunk(text, alphabet(text), start) }

    fun searchIn(original: String): BitapResult {
        val text = normalize(original)
        if (pattern == text) return BitapResult(isMatch = true, score = 0.0)
        val results = chunks.map { Bitap(text, it.pattern, it.alphabet, it.start, minMatchCharLength).search() }
        val any = results.any { it.isMatch }
        return BitapResult(any, if (any) results.sumOf { it.score } / chunks.size else 1.0)
    }

    /** The pattern in pieces of at most 32 characters, each with where it starts. */
    private fun chunk(pattern: String): List<Pair<Int, String>> = when {
        pattern.isEmpty() -> emptyList()
        pattern.length <= MAX_BITS -> listOf(0 to pattern)
        else -> {
            val remainder = pattern.length % MAX_BITS
            val end = pattern.length - remainder
            (0 until end step MAX_BITS).map { it to pattern.substring(it, it + MAX_BITS) } +
                listOfNotNull(
                    (pattern.length - MAX_BITS to pattern.substring(pattern.length - MAX_BITS)).takeIf {
                        remainder != 0
                    },
                )
        }
    }

    /** A piece of the pattern, the bit of each of its characters, and where it starts. */
    private class Chunk(val pattern: String, val alphabet: Map<Char, Int>, val start: Int)

    private fun alphabet(pattern: String): Map<Char, Int> {
        val mask = mutableMapOf<Char, Int>()
        pattern.forEachIndexed { i, char -> mask[char] = (mask[char] ?: 0) or (1 shl (pattern.length - i - 1)) }
        return mask
    }
}

/** Fuse's text normalisation for these options: lower case without accents. */
private fun normalize(text: String): String {
    val lower = text.lowercase()
    val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
    return NON_DECOMPOSABLE.entries.fold(decomposed) { acc, (from, to) -> acc.replace(from, to) }
}

private val COMBINING_MARKS = Regex("\\p{M}")
private val NON_DECOMPOSABLE = mapOf(
    "ł" to "l",
    "đ" to "d",
    "ø" to "o",
    "ħ" to "h",
    "ŧ" to "t",
    "ı" to "i",
    "ß" to "ss",
)
private const val MAX_BITS = 32
private const val DEFAULT_MIN_CHAR_LENGTH = 2
private const val NORM_SCALE = 1000.0
private val EPSILON = Math.ulp(1.0)

/**
 * Port of Fuse's Bitap `search` with location ignored and threshold 0.3: [pattern]'s best fuzzy match in [text],
 * scored by its errors over its length; it must cover [minMatchCharLength] characters in a row. [location] is where
 * a long pattern's chunk starts.
 */
private class Bitap(
    private val text: String,
    private val pattern: String,
    private val alphabet: Map<Char, Int>,
    location: Int,
    private val minMatchCharLength: Int,
) {
    private val patternLen = pattern.length
    private val expectedLocation = maxOf(0, minOf(location, text.length))
    private val computeMatches = minMatchCharLength > 1
    private val matchMask = BooleanArray(text.length)
    private val mask = 1 shl (patternLen - 1)
    private var currentThreshold = THRESHOLD
    private var bestLocation = -1
    private var finalScore = 1.0
    private var bestErrors = 0

    fun search(): BitapResult {
        markExactMatches()
        var lastBitArr = IntArray(0)
        var binMax = patternLen + text.length
        for (errors in 0 until patternLen) {
            binMax = window(errors, binMax)
            lastBitArr = scan(errors, binMax, lastBitArr)
            if (score(errors + 1) > currentThreshold) break
        }
        if (computeMatches && bestLocation >= 0) {
            val matchEnd = minOf(text.length - 1, bestLocation + patternLen - 1 + bestErrors)
            for (k in bestLocation..matchEnd) if (alphabet[text[k]] != null) matchMask[k] = true
        }
        val isMatch = bestLocation >= 0 && (!computeMatches || hasRun(matchMask, minMatchCharLength))
        return BitapResult(isMatch, maxOf(MIN_SCORE, finalScore))
    }

    private fun score(errors: Int) = errors.toDouble() / patternLen

    /** The exact occurrences: the threshold drops to an exact match's, and they're marked as matched. */
    private fun markExactMatches() {
        var from = expectedLocation
        var exact = text.indexOf(pattern, from)
        while (exact > -1) {
            currentThreshold = minOf(score(0), currentThreshold)
            from = exact + patternLen
            if (computeMatches) for (i in 0 until patternLen) matchMask[exact + i] = true
            exact = text.indexOf(pattern, from)
        }
    }

    /** The binary search of how far from the expected location a match with [errors] may be. */
    private fun window(errors: Int, binMax: Int): Int {
        var max = binMax
        var min = 0
        var mid = max
        while (min < mid) {
            if (score(errors) <= currentThreshold) min = mid else max = mid
            mid = (max - min) / 2 + min
        }
        return mid
    }

    /** One pass of the bit-parallel match with [errors], over the window [binMid]; its bit array. */
    private fun scan(errors: Int, binMid: Int, last: IntArray): IntArray {
        var start = maxOf(1, expectedLocation - binMid + 1)
        val finish = minOf(expectedLocation + binMid, text.length) + patternLen
        val bitArr = IntArray(finish + 2)
        bitArr[finish + 1] = (1 shl errors) - 1
        var j = finish
        while (j >= start) {
            val currentLocation = j - 1
            val charMatch = text.getOrNull(currentLocation)?.let { alphabet[it] } ?: 0
            bitArr[j] = ((bitArr[j + 1] shl 1) or 1) and charMatch
            if (errors != 0) {
                val previous = last.getOrElse(j + 1) { 0 } or last.getOrElse(j) { 0 }
                bitArr[j] = bitArr[j] or ((previous shl 1) or 1 or last.getOrElse(j + 1) { 0 })
            }
            if (bitArr[j] and mask != 0) {
                finalScore = score(errors)
                if (finalScore <= currentThreshold) {
                    currentThreshold = finalScore
                    bestLocation = currentLocation
                    bestErrors = errors
                    if (bestLocation <= expectedLocation) break
                    start = maxOf(1, 2 * expectedLocation - bestLocation)
                }
            }
            j--
        }
        return bitArr
    }
}

/** Port of `convertMaskToIndices`'s test: whether [mask] has [length] matched characters in a row. */
private fun hasRun(mask: BooleanArray, length: Int): Boolean {
    var run = 0
    mask.forEach { matched ->
        run = if (matched) run + 1 else 0
        if (run >= length) return true
    }
    return false
}

private const val THRESHOLD = 0.3
private const val MIN_SCORE = 0.001
