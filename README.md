# Pepper-mask-detection

1. Add the library as a dependency in build.gradle.
``` gradle
  
	dependencies {
	        implementation 'com.github.GeorgianIntegratedSystems:Frag-Pepper-mask-detection:0.1.0'
	}

	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

2. add the following dependencies in build.gradle, be sure to install OpenCV app on device.
``` gradle
    
	dependencies {
         
         // detection libs 
          implementation 'com.github.GeorgianIntegratedSystems:Frag-Pepper-mask-detection:0.1.0'
          implementation 'com.github.softbankrobotics-labs:pepper-mask-detection:master-SNAPSHOT'
          implementation project(":OpenCV")
           
          // conversation implementation
          implementation 'com.aldebaran:qisdk-conversationalcontent-greetings:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-robotabilities:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-volumecontrol:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-farewell:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-datetime:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-askrobotname:0.19.1-experimental-05'
          implementation 'com.aldebaran:qisdk-conversationalcontent-conversationbasics:0.19.1-experimental-05'
  }
```
3. add permissions in manifest:
``` kotlin
    <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
    <uses-permission android:name="android.permission.CAMERA" />
```

4. load openCV and check the camera permission - add the following code in the onCreate method and load OpenCV in onResume method:

``` kotlin
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

 public override fun onResume() {
        super.onResume()
        OpenCVUtils.loadOpenCV(this)
        detectmask.clearFaces(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
        detectmask.shouldBeRecognizing = true
        detectmask.startDetecting(binding.littleCard1.faceLayoutSmallCard,binding.littleCard2.faceLayoutSmallCard)
    }

```
5. add permissions result:
``` kotlin
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
```
6. add the following  code in onRobotFocusGained method to start Mask Detecting and Conversation:

``` kotlin
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
```

