package com.farmerbb.taskbar.virtualdisplay

import android.app.Presentation
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** DeX-inspired home surface rendered on the virtual display itself. */
class DexLauncherPresentation(context: Context, display: Display, private val launch: (String) -> Unit) : Presentation(context, display) {
    private val ink = Color.rgb(235, 242, 255)
    private val muted = Color.rgb(170, 188, 214)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(12))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(7, 18, 40), Color.rgb(17, 43, 76), Color.rgb(32, 35, 78)))
        }
        val header = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(context).apply {
            text = "Saku Desktop"
            textSize = 26f
            setTextColor(ink)
            typeface = android.graphics.Typeface.create("sans-serif-medium", 0)
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        header.addView(TextView(context).apply {
            text = "VIRTUAL DESKTOP  •  " + display.displayId
            textSize = 11f
            setTextColor(muted)
            gravity = Gravity.CENTER
            background = rounded(Color.rgb(19, 31, 54), 18)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        })
        root.addView(header)
        root.addView(TextView(context).apply {
            text = "Aplikasi"; textSize = 16f; setTextColor(ink); setPadding(0, dp(12), 0, dp(8))
        })
        val scroll = ScrollView(context)
        val grid = GridLayout(context).apply { columnCount = 6; useDefaultMargins = true; alignmentMode = GridLayout.ALIGN_BOUNDS }
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(72).forEach { info ->
                grid.addView(appTile(info, pm), GridLayout.LayoutParams().apply {
                    width = 0; height = dp(100)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(5), dp(4), dp(5), dp(4))
                })
            }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val taskbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(Color.argb(225, 13, 23, 42), 24)
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }
        taskbar.addView(TextView(context).apply {
            text = "S"; textSize = 18f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = rounded(Color.rgb(54, 105, 222), 16)
            setOnClickListener { scroll.smoothScrollTo(0, 0) }
        }, LinearLayout.LayoutParams(dp(42), dp(38)))
        taskbar.addView(TextView(context).apply {
            text = "  Beranda     •     Ketuk aplikasi untuk membuka"
            textSize = 12f; setTextColor(muted)
        }, LinearLayout.LayoutParams(0, dp(38), 1f))
        taskbar.addView(TextView(context).apply {
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            textSize = 13f; setTextColor(ink); gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(8), 0)
        })
        root.addView(taskbar, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        setContentView(root)
    }

    private fun appTile(info: ResolveInfo, pm: android.content.pm.PackageManager): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = rounded(Color.argb(75, 220, 235, 255), 16)
            isClickable = true; isFocusable = true; setPadding(dp(5), dp(7), dp(5), dp(6))
        }
        tile.addView(android.widget.ImageView(context).apply {
            setImageDrawable(info.loadIcon(pm)); scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        tile.addView(TextView(context).apply {
            text = info.loadLabel(pm).toString(); textSize = 11f; setTextColor(ink)
            gravity = Gravity.CENTER; maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(-1, dp(34)))
        tile.setOnClickListener { launch(info.activityInfo.packageName + "/" + info.activityInfo.name) }
        return tile
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
