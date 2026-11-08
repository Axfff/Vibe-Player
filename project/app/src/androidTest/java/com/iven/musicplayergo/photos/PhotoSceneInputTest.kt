package com.iven.musicplayergo.photos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoSceneInputTest {
    @Test fun twoViewsPreserveRgbOrderAndWholeFrameInsteadOfCroppingTwice() {
        val bitmap=Bitmap.createBitmap(4,2,Bitmap.Config.ARGB_8888)
        try {
            for (y in 0..1) for ((x,color) in listOf(Color.RED,Color.GREEN,Color.BLUE,Color.WHITE).withIndex()) bitmap.setPixel(x,y,color)
            val tensor=PhotoSceneInput.encode(bitmap)
            assertEquals(2*3*224*224,tensor.size)
            fun at(view: Int, channel: Int, x: Int, y: Int)=tensor[(view*3+channel)*224*224+y*224+x]
            // Central crop starts with green, while the full frame retains the left red edge.
            assertEquals((1f-.4578275f)/.26130258f,at(0,1,20,100),.001f)
            assertEquals((0f-.48145466f)/.26862954f,at(0,0,20,100),.001f)
            assertEquals((1f-.48145466f)/.26862954f,at(1,0,20,100),.001f)
            assertEquals((0f-.40821073f)/.27577711f,at(1,2,20,100),.001f)
            // Letterboxing uses approximately zero after CLIP normalization.
            for(c in 0..2) assertEquals(0f,at(1,c,100,10),.01f)
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }
}
