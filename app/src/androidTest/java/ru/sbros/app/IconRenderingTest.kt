package ru.sbros.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IconRenderingTest {
    @Test fun allReferenceVectorsInflateWithTheAndroidParser() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val icons=listOf(R.drawable.ic_spark,R.drawable.ic_inbox,R.drawable.ic_mic,R.drawable.ic_calendar,R.drawable.ic_shelves,
            R.drawable.ic_review,R.drawable.ic_bolt,R.drawable.ic_folder,R.drawable.ic_clock,R.drawable.ic_bulb,R.drawable.ic_note,
            R.drawable.ic_check,R.drawable.ic_chevron,R.drawable.ic_left,R.drawable.ic_arrowleft,R.drawable.ic_arrowright,R.drawable.ic_plus,
            R.drawable.ic_x,R.drawable.ic_sliders,R.drawable.ic_edit,R.drawable.ic_trash,R.drawable.ic_moon,R.drawable.ic_sun,
            R.drawable.ic_more,R.drawable.ic_smile,R.drawable.ic_wifi,R.drawable.ic_battery,R.drawable.ic_layers,R.drawable.ic_flag,
            R.drawable.ic_mail,R.drawable.ic_heart,R.drawable.ic_book,R.drawable.ic_quote,R.drawable.ic_feather,R.drawable.ic_wand,R.drawable.ic_search,
            R.drawable.ic_bolt_nav,R.drawable.ic_feather_nav,R.drawable.ic_mic_nav,R.drawable.ic_calendar_nav,R.drawable.ic_review_nav,R.drawable.ic_mic_hero)
        icons.forEach {id->val drawable=requireNotNull(context.getDrawable(id));drawable.setBounds(0,0,96,96);drawable.draw(Canvas(Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)))}
    }
    @Test fun microphoneContainsTheLowerPickupArc() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap=Bitmap.createBitmap(240,240,Bitmap.Config.ARGB_8888)
        val drawable=requireNotNull(context.getDrawable(R.drawable.ic_mic))
        drawable.setBounds(0,0,240,240)
        drawable.draw(Canvas(bitmap))
        var pickupPixels=0
        // The SVG pickup arc passes here; capsule and stem cannot produce these pixels.
        for(y in 115..165) for(x in 45..85) if(Color.alpha(bitmap.getPixel(x,y))>100)pickupPixels++
        val folder=File(context.getExternalFilesDir(null),"icon-check").apply {mkdirs()}
        File(folder,"mic.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(folder,"metrics.txt").writeText("pickupArcPixels=$pickupPixels")
        assertTrue("The reference microphone pickup arc is missing: $pickupPixels pixels",pickupPixels>50)
    }
}
