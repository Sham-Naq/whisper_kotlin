package com.example.whisper_kotlin.util

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lightweight profiling utility for the transcription pipeline.
 *
 * Usage:
 * val prof = TranscriptionProfiler.session("Transcribe")
 * prof.time("preprocess") { preprocessAudio(input) }
 * prof.timeSuspend("feature-extract") { extractFeaturesAsync(input) }
 * prof.timeSuspend("inference") { runModel(features) }
 * prof.time("decode") { decodeTokens(tokens) }
 * prof.time("postprocess") { writeOutput(text) }
 * prof.report() // Logs a detailed breakdown with totals and percentages
 */
object TranscriptionProfiler {
    private const val TAG = "TranscriptionProfiler"

    fun session(label: String): Session = Session(label)

    data class Span(
        val name: String,
        val startNs: Long,
        var endNs: Long = 0L
    ) {
        val durationNs: Long get() = (if (endNs == 0L) SystemClock.elapsedRealtimeNanos() else endNs) - startNs
    }

    class Session(private val label: String) {
        private val spans = mutableListOf<Span>()
        private val t0 = SystemClock.elapsedRealtimeNanos()
        private var closed = false

        /** Profile a synchronous block. */
        fun <T> time(name: String, block: () -> T): T {
            val s = Span(name, SystemClock.elapsedRealtimeNanos())
            return try {
                block()
            } finally {
                s.endNs = SystemClock.elapsedRealtimeNanos()
                spans += s
                Log.d(TAG, "[$label] $name took ${fmtNs(s.durationNs)}")
            }
        }

        /** Profile a suspend block. Executes on Dispatchers.Default to avoid blocking the main thread. */
        suspend fun <T> timeSuspend(name: String, block: suspend () -> T): T {
            val s = Span(name, SystemClock.elapsedRealtimeNanos())
            return try {
                withContext(Dispatchers.Default) { block() }
            } finally {
                s.endNs = SystemClock.elapsedRealtimeNanos()
                spans += s
                Log.d(TAG, "[$label] $name took ${fmtNs(s.durationNs)}")
            }
        }

        /** Start a long-running span, returns its index. Pair with end(). */
        fun start(name: String): Int {
            spans += Span(name, SystemClock.elapsedRealtimeNanos())
            return spans.lastIndex
        }

        /** End a previously started span by index. */
        fun end(index: Int) {
            if (index in spans.indices) {
                spans[index].endNs = SystemClock.elapsedRealtimeNanos()
                Log.d(TAG, "[$label] ${spans[index].name} took ${fmtNs(spans[index].durationNs)}")
            }
        }

        /** Add a point-in-time marker (0 duration). Useful for milestones. */
        fun mark(name: String) {
            val now = SystemClock.elapsedRealtimeNanos()
            Log.d(TAG, "[$label] mark: $name at +${fmtNs(now - t0)}")
        }

        /** Log a full report sorted by descending duration, including totals and percentages. */
        fun report() {
            if (closed) return
            closed = true
            val t1 = SystemClock.elapsedRealtimeNanos()
            val total = t1 - t0
            val nonZero = spans.filter { it.durationNs > 0 }
            val header = buildString {
                append("[$label] Transcription timing summary — total ${fmtNs(total)}")
                append("\n----------------------------------------------")
            }
            Log.d(TAG, header)
            nonZero.sortedByDescending { it.durationNs }.forEach { s ->
                val pct = (s.durationNs.toDouble() / total.toDouble()) * 100.0
                Log.d(TAG, String.format("[$label]  • %-18s %12s  (%5.1f%%)", s.name, fmtNs(s.durationNs), pct))
            }
            Log.d(TAG, "[$label] ----------------------------------------------")
        }

        fun clear() {
            spans.clear()
        }
    }

    private fun fmtNs(ns: Long): String {
        // Prefer ms if >= 1 ms, else show µs or ns for tiny sections
        val ms = ns / 1_000_000.0
        return when {
            ms >= 1000 -> String.format("%.2fs", ms / 1000.0)
            ms >= 1 -> String.format("%.2fms", ms)
            else -> String.format("%.0fµs", ns / 1000.0)
        }
    }
}
