package com.attendpro.store

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt

object VoiceSignatureEngine {
    data class Capture(val template: String = "", val quality: Int = 0, val error: String = "")
    private const val RATE = 16_000
    private const val FRAME = 400
    private const val HOP = 200
    private val frequencies = doubleArrayOf(120.0,170.0,230.0,310.0,410.0,540.0,700.0,900.0,1140.0,1430.0,1770.0,2170.0,2620.0,3130.0,3700.0,4300.0)

    @SuppressLint("MissingPermission")
    fun capture(callback: (Capture) -> Unit) {
        thread(name="attend-voice-capture",isDaemon=true) {
            val min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
            if(min<=0){callback(Capture(error="الميكروفون غير متاح"));return@thread}
            val recorder=listOf(MediaRecorder.AudioSource.VOICE_RECOGNITION,MediaRecorder.AudioSource.UNPROCESSED,MediaRecorder.AudioSource.MIC).firstNotNullOfOrNull { source ->
                runCatching { AudioRecord(source,RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(min*2,8192)).takeIf { it.state==AudioRecord.STATE_INITIALIZED } }.getOrNull()
            }
                ?:run{callback(Capture(error="تعذر فتح الميكروفون"));return@thread}
            val samples=ShortArray(RATE*4);var offset=0
            try{recorder.startRecording();while(offset<samples.size){val n=recorder.read(samples,offset,minOf(2048,samples.size-offset));if(n<=0)break else offset+=n}}
            catch(e:Exception){callback(Capture(error=e.message?:"فشل تسجيل الصوت"));return@thread}
            finally{runCatching{recorder.stop()};recorder.release()}
            if(offset<RATE){callback(Capture(error="التسجيل قصير جدًا"));return@thread}
            val data=samples.copyOf(offset)
            val clipping=data.count{kotlin.math.abs(it.toInt())>=32000}.toDouble()/data.size
            if(clipping>0.035){callback(Capture(error="الصوت مرتفع ومشوّه؛ ابتعد قليلًا عن الميكروفون"));return@thread}
            val result=signature(data)
            if(result.first==null){callback(Capture(error=result.third));return@thread}
            callback(Capture(encode(result.first!!),result.second))
        }
    }

    fun appendTemplate(existing:String,fresh:String,maxTemplates:Int=5):String{
        val all=(unpack(existing)+unpack(fresh)).takeLast(maxTemplates)
        return if(all.size<=1)all.firstOrNull().orEmpty() else "vm2:"+all.joinToString("~")
    }

    fun similarity(a:String,b:String):Float{
        val scores=unpack(a).flatMap{x->unpack(b).map{y->cosine(decode(x),decode(y))}}.sortedDescending()
        if(scores.isEmpty())return 0f
        return when(scores.size){1->scores[0];2->scores[0]*0.72f+scores[1]*0.28f;else->scores[0]*0.55f+scores[1]*0.30f+scores[2]*0.15f}
    }

    private fun signature(data:ShortArray):Triple<FloatArray?,Int,String>{
        val frameRms=mutableListOf<Double>();var start=0
        while(start+FRAME<=data.size){var energy=0.0;for(i in 0 until FRAME){val v=data[start+i]/32768.0;energy+=v*v};frameRms.add(sqrt(energy/FRAME));start+=HOP}
        if(frameRms.isEmpty())return Triple(null,0,"لم يصل صوت صالح")
        val sorted=frameRms.sorted();val noise=sorted[(sorted.size*0.2).toInt().coerceIn(0,sorted.lastIndex)].coerceAtLeast(0.002)
        val threshold=maxOf(0.009,noise*2.4);val voiced=frameRms.indices.filter{frameRms[it]>=threshold}
        if(voiced.size<18)return Triple(null,0,"لم يُسمع كلام كافٍ؛ قل العبارة كاملة بصوت طبيعي")
        val frames=ArrayList<FloatArray>(voiced.size);var zcrSum=0.0
        voiced.forEach{index->
            val frameStart=index*HOP;val bands=FloatArray(frequencies.size)
            frequencies.forEachIndexed{i,f->bands[i]=ln(1.0+goertzel(data,frameStart,FRAME,f)).toFloat()}
            normalize(bands);frames.add(bands);var crossings=0
            for(i in 1 until FRAME)if((data[frameStart+i-1]<0)!=(data[frameStart+i]<0))crossings++
            zcrSum+=crossings.toDouble()/FRAME
        }
        val vector=FloatArray(frequencies.size*2+2)
        for(band in frequencies.indices){val mean=frames.sumOf{it[band].toDouble()}/frames.size;val variance=frames.sumOf{val d=it[band]-mean;(d*d).toDouble()}/frames.size;vector[band]=mean.toFloat();vector[frequencies.size+band]=sqrt(variance).toFloat()}
        vector[vector.lastIndex-1]=(zcrSum/frames.size).toFloat();vector[vector.lastIndex]=voiced.size.toFloat()/frameRms.size;normalize(vector)
        val speechLevel=voiced.map{frameRms[it]}.average();val snr=(speechLevel/noise).coerceIn(1.0,12.0)
        val quality=(((snr-1.0)/11.0*60)+(voiced.size.toDouble()/frameRms.size).coerceAtMost(0.65)/0.65*40).toInt().coerceIn(1,100)
        return Triple(vector,quality,"")
    }

    private fun normalize(values:FloatArray){val mean=values.average().toFloat();var norm=0.0;for(i in values.indices){values[i]-=mean;norm+=values[i]*values[i]};val scale=sqrt(norm).toFloat().coerceAtLeast(1e-6f);for(i in values.indices)values[i]/=scale}
    private fun goertzel(data:ShortArray,start:Int,length:Int,frequency:Double):Double{val coeff=2.0*cos(2.0*PI*frequency/RATE);var s0:Double;var s1=0.0;var s2=0.0;for(i in 0 until length){val window=0.54-0.46*cos(2.0*PI*i/(length-1));s0=data[start+i]/32768.0*window+coeff*s1-s2;s2=s1;s1=s0};return(s1*s1+s2*s2-coeff*s1*s2).coerceAtLeast(0.0)}
    private fun unpack(value: String): List<String> = when {
        value.startsWith("vm2:") || value.startsWith("vm1:") -> value.substring(4).split('~').filter { it.startsWith("vs2:") || it.startsWith("vs1:") }
        value.startsWith("vs2:") || value.startsWith("vs1:") -> listOf(value)
        else -> emptyList()
    }
    private fun encode(v:FloatArray):String{val b=ByteBuffer.allocate(v.size*4).order(ByteOrder.LITTLE_ENDIAN);v.forEach{b.putFloat(it)};return "vs2:"+Base64.encodeToString(b.array(),Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)}
    private fun decode(v:String):FloatArray?=runCatching{val raw=Base64.decode(v.substring(4),Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING);val b=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);FloatArray(raw.size/4){b.float}}.getOrNull()
    private fun cosine(a:FloatArray?,b:FloatArray?):Float{if(a==null||b==null||a.size!=b.size)return 0f;var dot=0.0;var x=0.0;var y=0.0;for(i in a.indices){dot+=a[i]*b[i];x+=a[i]*a[i];y+=b[i]*b[i]};return if(x<=0||y<=0)0f else(dot/sqrt(x*y)).toFloat().coerceIn(-1f,1f)}
}
