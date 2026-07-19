package com.blueprinticons.pack

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this)
        text.textSize = 16f
        text.setPadding(64, 128, 64, 64)
        text.text = """
            📐 Blueprint Icons is installed!

            To apply it:

            1. Make sure your home screen is the Nothing Launcher
               (Settings → Apps → Default apps → Home app).

            2. Long-press an empty spot on the home screen.

            3. Tap Customisation (or Home settings) → Icon pack.

            4. Choose Blueprint Icons.

            Frosted drafting-glass icons with grid and guide
            lines. Apps without a hand-made icon get a matching
            frosted tile automatically.

            Tip: this style shines on darker wallpapers.
        """.trimIndent()
        setContentView(text)
    }
}
