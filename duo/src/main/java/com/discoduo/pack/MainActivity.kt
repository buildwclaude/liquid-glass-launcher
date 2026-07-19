package com.discoduo.pack

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
            ✨ Disco Duo is installed!

            This pack changes ONLY two icons:

            • YouTube — red disco play button
            • Claude — cream disco starburst on orange

            Every other app keeps its normal icon.

            To apply: long-press the home screen →
            Customisation (or Home settings) → Icon pack →
            Disco Duo.
        """.trimIndent()
        setContentView(text)
    }
}
