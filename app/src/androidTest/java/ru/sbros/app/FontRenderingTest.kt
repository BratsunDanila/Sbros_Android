package ru.sbros.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.sbros.app.core.designsystem.Onest

/** Tests actual Compose-resolved glyphs on the device without changing its UI. */
@RunWith(AndroidJUnit4::class)
class FontRenderingTest {
    @Test fun regularBoldAndHeadingHaveDifferentGlyphWeight() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val resolver=createFontFamilyResolver(context)
        val output=Bitmap.createBitmap(400,240,Bitmap.Config.ARGB_8888)
        Canvas(output).drawColor(Color.WHITE)
        val ink = listOf(400, 700, 760).associateWith { weight ->
            val typeface=resolver.resolve(Onest,FontWeight(weight)).value as Typeface
            val bitmap=Bitmap.createBitmap(400,80,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface=typeface; textSize=40f; color=Color.BLACK }
            canvas.drawText("Дела. Сброс",8f,52f,paint)
            Canvas(output).drawBitmap(bitmap,0f,when(weight){400->0f;700->80f;else->160f},null)
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            pixels.sumOf { (255-Color.red(it)).toLong() }.toDouble()/255.0
        }
        Log.i("SbrosFontTest", "Rendered ink mass: $ink")
        val folder = File(context.getExternalFilesDir(null),"font-check").apply { mkdirs() }
        File(folder,"weights.png").outputStream().use {
            output.compress(Bitmap.CompressFormat.PNG,100,it)
        }
        File(folder,"metrics.txt").writeText(ink.toString())
        assertTrue("Bold must visibly differ from regular: $ink", ink.getValue(700)>ink.getValue(400)*1.20)
        assertTrue("Heading must visibly differ from regular: $ink", ink.getValue(760)>ink.getValue(400)*1.20)
    }
}
