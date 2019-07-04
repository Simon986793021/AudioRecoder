package com.sw.audiorecorder

import android.Manifest
import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import com.sw.audiorecorder.AudioConfig.AUDIO_FORMAT
import com.sw.audiorecorder.AudioConfig.CHANNEL_CONFIG
import com.sw.audiorecorder.AudioConfig.SAMPLE_RATE_INHZ
import kotlinx.android.synthetic.main.activity_main.*
import java.io.*
import java.util.ArrayList

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private val MY_PERMISSIONS_REQUEST = 1001
    /**
     * 需要申请的运行时权限
     */
    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    /**
     * 被用户拒绝的权限列表
     */
    private val mPermissionList = ArrayList<String>()
    private var isRecording: Boolean = false
    private lateinit var audioRecord: AudioRecord
    private lateinit var audioTrack: AudioTrack
    private var audioData: ByteArray? = null
    private lateinit var fileInputStream: FileInputStream

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        button.setOnClickListener(this)
        button2.setOnClickListener(this)
        button3.setOnClickListener(this)
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (i in 0 until permissions.size) {
                if (ContextCompat.checkSelfPermission(this, permissions[i]) != PackageManager.PERMISSION_GRANTED) {
                    mPermissionList.add(permissions[i])
                }
            }
            if (!mPermissionList.isEmpty()) {
                val permissions = mPermissionList.toTypedArray()
                ActivityCompat.requestPermissions(this, permissions, MY_PERMISSIONS_REQUEST)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.i("SIMON", permissions[i] + " 权限被用户禁止！")
                }
            }
        }
    }

    override fun onClick(v: View?) {
        when (v) {
            button -> {
                if (button.text.toString() == "开始录音") {
                    button.text = "停止录音"
                    startRecord()
                } else {
                    button.text = "开始录音"
                    stopRecord()
                }
            }
            button2 -> {
                val pcmToWavUtil = PcmToWavUtil(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT)
                val pcmFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio.pcm")
                val wavFile = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio.wav")
                if (!wavFile.mkdirs()) {
                    Log.e("SIMON", "wavFile Directory not created")
                }
                if (wavFile.exists()) {
                    wavFile.delete()
                }
                pcmToWavUtil.pcmToWav(pcmFile.absolutePath, wavFile.absolutePath)

            }
            button3 -> {
                if (button3.text.toString() == "播放") {
                    button3.text = "停止"
                    play()
                } else {
                    button3.text = "播放"
                    stop()
                }
            }
        }
    }

    /**
     * 停止播放
     */
    private fun stop() {
        if (audioTrack != null) {
            audioTrack.stop()
            audioTrack.release()
        }
    }

    /**
     * 开始播放
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun play() {
        val channalConfig = AudioFormat.CHANNEL_OUT_MONO
        val minBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_INHZ, channalConfig, AUDIO_FORMAT)
        audioTrack = AudioTrack(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(), AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE_INHZ)
                .setEncoding(AUDIO_FORMAT)
                .setChannelMask(channalConfig)
                .build(), minBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE)
        audioTrack.play()

        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio.pcm")
            fileInputStream = FileInputStream(file)
            Thread {
                try {
                    val tempBuffer = ByteArray(minBufferSize)
                    while (fileInputStream.available() > 0) {
                        val readCount = fileInputStream.read(tempBuffer)
                        if (readCount == AudioTrack.ERROR_INVALID_OPERATION || readCount == AudioTrack.ERROR_BAD_VALUE) {
                            continue
                        }
                        if (readCount != 0 && readCount != -1) {
                            audioTrack.write(tempBuffer, 0, readCount)
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    /**
     * 停止录音
     */
    private fun stopRecord() {
        isRecording = false
        if (audioRecord != null) {
            audioRecord.stop()
            audioRecord.release()
        }
    }

    /**
     * 开始录音
     */
    private fun startRecord() {
        val minBufferSize: Int = AudioRecord.getMinBufferSize(SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE_INHZ, CHANNEL_CONFIG, AUDIO_FORMAT, minBufferSize)
        val data = ByteArray(minBufferSize)
        val file = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "audio.pcm")
        if (!file.mkdirs()) {
            Log.e("SIMON", "Directory not created")
        }
        if (file.exists()) {
            file.delete()
        }
        audioRecord.startRecording()
        isRecording = true

        Thread(Runnable {
            var os: FileOutputStream? = null
            try {
                os = FileOutputStream(file)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
            }

            if (null != os) {
                while (isRecording) {
                    val read = audioRecord.read(data, 0, minBufferSize)
                    // 如果读取音频数据没有出现错误，就将数据写入到文件
                    if (AudioRecord.ERROR_INVALID_OPERATION != read) {
                        try {
                            os.write(data)
                        } catch (e: IOException) {
                            e.printStackTrace()
                        }

                    }
                }
                try {
                    Log.i("SIMON", "run: close file output stream !")
                    os.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }).start()
    }
}
