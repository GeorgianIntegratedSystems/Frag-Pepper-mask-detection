package com.android.peppermaskdetection

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.conversation.BodyLanguageOption
import com.aldebaran.qi.sdk.builder.QiChatbotBuilder
import com.aldebaran.qi.sdk.builder.TopicBuilder
import com.aldebaran.qi.sdk.conversationalcontentlibrary.askrobotname.AskRobotNameConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.base.AbstractConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.base.ConversationalContentChatBuilder
import com.aldebaran.qi.sdk.conversationalcontentlibrary.datetime.DateTimeConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.farewell.FarewellConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.greetings.GreetingsConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.robotabilities.RobotAbilitiesConversationalContent
import com.aldebaran.qi.sdk.conversationalcontentlibrary.volumecontrol.VolumeControlConversationalContent
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.android.peppermaskdetection.databinding.ActivityMainBinding
import com.softbankrobotics.facemaskdetection.FaceMaskDetection
import com.softbankrobotics.facemaskdetection.capturer.BottomCameraCapturer
import com.softbankrobotics.facemaskdetection.capturer.TopCameraCapturer
import com.softbankrobotics.facemaskdetection.detector.AizooFaceMaskDetector
import com.softbankrobotics.facemaskdetection.utils.OpenCVUtils
import com.softbankrobotics.facemaskdetection.utils.TAG
import ge.gis.maskdetection.DetectMaskTools

class MainActivity : RobotActivity(),RobotLifecycleCallbacks {

    lateinit var binding:ActivityMainBinding
    lateinit var detectmask: DetectMaskTools


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        detectmask = DetectMaskTools(this)
        detectmask.clearFaces(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
        if (detectmask.useTopCamera || detectmask.cameraPermissionAlreadyGranted()) {
            // No need to request permission
            QiSDK.register(this, this)
        } else {
            // First launch, needs to ask permission
            detectmask.requestPermissionForCamera()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == detectmask.CAMERA_PERMISSION_REQUEST_CODE) {
            var cameraPermissionGranted = true

            for (grantResult in grantResults) {
                cameraPermissionGranted = cameraPermissionGranted and
                        (grantResult == PackageManager.PERMISSION_GRANTED)
            }
            if (cameraPermissionGranted) {
                QiSDK.register(this, this)
            } else {
                Toast.makeText(
                    this,
                    R.string.permissions_needed,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.i(TAG, "onRobotFocusGained")
        if (detectmask.chat == null) {
            val topic = TopicBuilder.with(qiContext).withResource(R.raw.chat).build()
            val qiChatbot = QiChatbotBuilder.with(qiContext).withTopic(topic).build()
            detectmask.qiChatbot = qiChatbot
            detectmask.maskBookmark = topic.bookmarks["GREETING_MASK"]
            detectmask.noMaskBookmark = topic.bookmarks["GREETING_NO_MASK"]
            detectmask.tookOffMaskBookmark = topic.bookmarks["TOOK_OFF_MASK"]
            detectmask.putOnMaskBookmark = topic.bookmarks["PUT_ON_MASK"]
            detectmask.newWithoutMaskBookmark = topic.bookmarks["NEW_WITHOUT_MASK"]
            detectmask.manyPeopleBookmark = topic.bookmarks["MANY_PEOPLE"]

            val conversationalContents: List<AbstractConversationalContent> = listOf(
                GreetingsConversationalContent(),
                FarewellConversationalContent(),
                AskRobotNameConversationalContent(),
                DateTimeConversationalContent(),
                RobotAbilitiesConversationalContent(),
                VolumeControlConversationalContent()
            )

            detectmask.chat = ConversationalContentChatBuilder.with(qiContext)
                .withChatbot(qiChatbot)
                .withConversationalContents(conversationalContents)
                .build()
            detectmask.chat?.listeningBodyLanguage = BodyLanguageOption.DISABLED
        }

        Log.i(TAG, "Initialised chat")

        val capturer = if (detectmask.useTopCamera) {
            TopCameraCapturer(qiContext)
        } else {
            BottomCameraCapturer(this, this)
        }
        val detector = AizooFaceMaskDetector(this)
        detectmask.detection = FaceMaskDetection(detector, capturer)
        detectmask.shouldBeRecognizing = true
        detectmask.startDetecting(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
        Log.i(TAG, "Starting chat")
        detectmask.chatFuture = detectmask.chat?.async()?.run()
    }

    override fun onRobotFocusLost() {
        Log.w(TAG, "Robot focus lost")
        detectmask.detectionFuture?.cancel(true)
    }

    override fun onRobotFocusRefused(reason: String?) {
        Log.e(TAG, "Robot focus refused because $reason")
    }


    public override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause")
        detectmask.shouldBeRecognizing = false
        detectmask.detectionFuture?.requestCancellation()
        detectmask.detectionFuture = null
    }

    public override fun onResume() {
        super.onResume()
        OpenCVUtils.loadOpenCV(this)
        detectmask.clearFaces(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
        detectmask.shouldBeRecognizing = true
        detectmask.startDetecting(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
    }

    override fun onDestroy() {
        super.onDestroy()
        detectmask.detectionFuture?.requestCancellation()
        QiSDK.unregister(this)
    }
}