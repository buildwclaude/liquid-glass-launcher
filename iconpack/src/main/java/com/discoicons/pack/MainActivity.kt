package com.discoicons.pack

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

// Opening the icon pack just explains how to apply it — the actual work
// is done by whichever launcher reads the pack.
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.textSize = 16f
        text.setPadding(64, 128, 64, 64)
        text.text = """
            ✨ Disco Icons is installed!

            To apply it:

            1. Make sure your home screen is the Nothing Launcher
               (Settings → Apps → Default apps → Home app).

            2. Long-press an empty spot on the home screen.

            3. Tap Customisation (or Home settings) → Icon pack.

            4. Choose Disco Icons.

            Apps without a hand-made disco icon get a silver
            mirror-tile frame automatically.

            Works with any launcher that supports icon packs
            (Nothing Launcher, Nova, Lawnchair, …).
        """.trimIndent()
        setContentView(text)
    }
}
