package com.anthony.skywidget.sky

import android.graphics.Color

/**
 * Sky gradient palette. Given a sun altitude and a direction (rising vs setting),
 * returns a two-color gradient plus an appropriate text color.
 *
 * Palette was tuned visually in Stage 3 of the HTML mockup. If you want to tweak
 * anchor colors, the only change needed is editing [RISING] and [SETTING] below —
 * interpolation picks up new values automatically.
 */
object SkyPalette {

    /** A vertical two-stop linear gradient: [top] at 0%, [bot] at 100%. */
    data class Gradient(val topArgb: Int, val botArgb: Int, val textArgb: Int)

    /** One anchor in the palette curve. */
    private data class Anchor(val altDeg: Double, val top: IntArray, val bot: IntArray)

    private fun rgb(r: Int, g: Int, b: Int) = intArrayOf(r, g, b)

    private val RISING: Array<Anchor> = arrayOf(
        Anchor(-18.0, rgb(14,  26, 58),  rgb(42,  55,  102)),  // night
        Anchor(-10.0, rgb(26,  34, 72),  rgb(90,  74,  117)),  // nautical twilight
        Anchor(-4.0,  rgb(63,  58, 107), rgb(199, 138, 158)),  // civil twilight (dawn)
        Anchor(3.0,   rgb(217, 106, 76), rgb(245, 194, 122)),  // sunrise
        Anchor(12.0,  rgb(107, 179, 221),rgb(185, 218, 232)),  // morning
        Anchor(30.0,  rgb(74,  155, 207),rgb(141, 200, 230)),  // mid-morning
        Anchor(70.0,  rgb(74,  155, 207),rgb(141, 200, 230))   // noon clamp
    )

    private val SETTING: Array<Anchor> = arrayOf(
        Anchor(-18.0, rgb(14,  26, 58),  rgb(42,  55,  102)),
        Anchor(-10.0, rgb(30,  28, 70),  rgb(100, 70,  110)),
        Anchor(-4.0,  rgb(63,  58, 107), rgb(199, 138, 158)),
        Anchor(3.0,   rgb(217, 106, 76), rgb(245, 194, 122)),
        Anchor(12.0,  rgb(107, 179, 221),rgb(185, 218, 232)),
        Anchor(30.0,  rgb(74,  155, 207),rgb(141, 200, 230)),
        Anchor(70.0,  rgb(74,  155, 207),rgb(141, 200, 230))
    )

    fun resolve(altDeg: Double, setting: Boolean): Gradient {
        val palette = if (setting) SETTING else RISING
        val (top, bot) = interpolate(palette, altDeg)
        val textArgb = textColorForAlt(altDeg)
        return Gradient(
            topArgb = Color.rgb(top[0], top[1], top[2]),
            botArgb = Color.rgb(bot[0], bot[1], bot[2]),
            textArgb = textArgb
        )
    }

    private fun interpolate(palette: Array<Anchor>, alt: Double): Pair<IntArray, IntArray> {
        if (alt <= palette.first().altDeg) return palette.first().top to palette.first().bot
        if (alt >= palette.last().altDeg) return palette.last().top to palette.last().bot
        for (i in 0 until palette.size - 1) {
            val a = palette[i]
            val b = palette[i + 1]
            if (alt in a.altDeg..b.altDeg) {
                val t = (alt - a.altDeg) / (b.altDeg - a.altDeg)
                return lerp(a.top, b.top, t) to lerp(a.bot, b.bot, t)
            }
        }
        return palette.first().top to palette.first().bot
    }

    private fun lerp(a: IntArray, b: IntArray, t: Double): IntArray {
        return intArrayOf(
            (a[0] + (b[0] - a[0]) * t).toInt(),
            (a[1] + (b[1] - a[1]) * t).toInt(),
            (a[2] + (b[2] - a[2]) * t).toInt()
        )
    }

    private fun textColorForAlt(alt: Double): Int = when {
        alt >= 0.0  -> Color.parseColor("#2E1608") // dark text on bright sky
        alt >= -6.0 -> Color.parseColor("#1F1430") // civil twilight: still dark-ish
        else        -> Color.parseColor("#E8E4D9") // light text on dark sky
    }
}
