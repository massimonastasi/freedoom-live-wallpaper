/*
 * Freedoom Live Wallpaper
 * Copyright (C) 2026 Massimo Nastasi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms
 * of the GNU General Public License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details. You should have received a copy in
 * the file LICENSE; see also NOTICE.md for the third-party notices this work depends on.
 *
 * It is GPL-2.0 because it reproduces gameplay constants and tables from the id Software
 * engine source release (linuxdoom-1.10), which is GPL-2.0. Every such value carries a
 * comment naming the file and symbol it came from; those comments are the attribution the
 * licence requires and must not be removed.
 */
package io.github.massimonastasi.freedoomlw

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * What this application is built from, and under what terms.
 *
 * The text is read from the shipped NOTICE.md and LICENSE rather than restated here, so the
 * screen cannot drift from the files that carry the actual obligations. Sections are split on
 * the markdown headings; the headings become the labels the design draws above each block.
 *
 * This is a legal notice, so it is deliberately not summarised: the GPL requires the licence
 * text to travel with the work, and a paraphrase of an attribution is not an attribution.
 */
class LicencesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.licences)

        findViewById<MaterialToolbar>(R.id.toolbar).apply {
            setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        }

        val container = findViewById<LinearLayout>(R.id.blocks)
        val inflater = LayoutInflater.from(this)
        for ((label, body) in blocks()) {
            val block = inflater.inflate(R.layout.licence_block, container, false)
            block.findViewById<TextView>(R.id.block_label).text = label
            block.findViewById<TextView>(R.id.block_body).text = body
            container.addView(block)
        }
    }

    /**
     * NOTICE.md split into labelled sections, then the licence itself.
     *
     * The markup is stripped rather than rendered - see clean().
     */
    private fun blocks(): List<Pair<String, String>> {
        val notice = read("NOTICE.md") ?: return listOf(
            getString(R.string.licences_heading) to getString(R.string.licences_missing, "NOTICE.md")
        )
        val out = ArrayList<Pair<String, String>>()

        // Everything before the first "## " heading is the statement of what licence this
        // work is under, which is the first thing a reader needs.
        val parts = notice.split("\n## ")
        out += getString(R.string.licences_heading) to clean(parts.first())
        for (part in parts.drop(1)) {
            val label = part.substringBefore('\n').trim()
            out += label to clean(part.substringAfter('\n'))
        }

        read("LICENSE")?.let { out += getString(R.string.licences_gpl) to it.trim() }
        return out
    }

    private fun read(name: String): String? =
        try { assets.open(name).bufferedReader().use { it.readText() } } catch (e: Exception) { null }

    /**
     * Markdown to plain text, by hand.
     *
     * Not a renderer: this is four substitutions against a document whose markup is four
     * things, and a markdown library would be a dependency added so that a legal notice could
     * have bold text. What it must not do is change a word - the emphasis markers go, the
     * quote markers go, a link becomes its own label, and nothing else moves.
     */
    private fun clean(text: String): String = text
        .lineSequence()
        .filterNot { it.trim() == "---" || it.startsWith("# ") }
        .map { it.removePrefix("> ").removePrefix(">") }
        .joinToString("\n")
        .replace(Regex("""\[([^]]+)]\([^)]*\)"""), "$1")
        .replace("**", "")
        .replace("`", "")
        .replace(Regex("""(?<!\*)\*(?!\*)"""), "")
        .trim()
}
