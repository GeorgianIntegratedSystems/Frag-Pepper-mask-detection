package ge.gis.maskdetection

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.`object`.conversation.*
import com.softbankrobotics.facemaskdetection.FaceMaskDetection
import com.softbankrobotics.facemaskdetection.detector.FaceMaskDetector
import com.softbankrobotics.facemaskdetection.utils.TAG

class DetectMaskTools(val activity:Activity) {

    var qiChatbot: QiChatbot? = null
    var chatFuture: Future<Void>? = null
    val useTopCamera = true
    var shouldBeRecognizing = false

    var detection: FaceMaskDetection? = null
    var detectionFuture: Future<Unit>? = null

    val CAMERA_PERMISSION_REQUEST_CODE = 1

    var engaged = false
    var sawNobodyCount = 0

    var lastSawWithoutMask = false
    var annoyance = 0

    var lastMentionedPeopleNum = 0
    var worthMentioningPeopleCounter = 0


    var chat: Chat? = null
    var maskBookmark: Bookmark? = null
    var noMaskBookmark: Bookmark? = null
    var tookOffMaskBookmark: Bookmark? = null
    var putOnMaskBookmark: Bookmark? = null
    var newWithoutMaskBookmark: Bookmark? = null
    var manyPeopleBookmark: Bookmark? = null

    fun requestPermissionForCamera() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
            Toast.makeText(
                activity,
                R.string.permissions_needed,
                Toast.LENGTH_LONG
            ).show()
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        }
    }

    fun cameraPermissionAlreadyGranted(): Boolean {
        val resultCamera = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
        return resultCamera == PackageManager.PERMISSION_GRANTED
    }

    inner class FacesForDisplay(rawFaces: List<FaceMaskDetector.DetectedFace>) {
        // Choose the "main" focused faced, which is either the biggest or, when there are a lot of
        // people, the one in the middle.
        val mainFace: FaceMaskDetector.DetectedFace? = when {
            rawFaces.size >= 5 -> rawFaces[2]
            rawFaces.size == 4 -> rawFaces.subList(1, 3).maxBy { it.size() }
            else -> rawFaces.maxBy { it.size() }
        }
        private val mainFaceIndex = rawFaces.indexOf(mainFace)

        // Set the other faces relatively
        val leftFarFace: FaceMaskDetector.DetectedFace? = rawFaces.getOrNull(mainFaceIndex - 2)
        val leftNearFace: FaceMaskDetector.DetectedFace? = rawFaces.getOrNull(mainFaceIndex - 1)
        val rightNearFace: FaceMaskDetector.DetectedFace? = rawFaces.getOrNull(mainFaceIndex + 1)
        val rightFarFace: FaceMaskDetector.DetectedFace? = rawFaces.getOrNull(mainFaceIndex + 2)
    }

    fun setFaces(faces: List<FaceMaskDetector.DetectedFace>,view1: View,view2: View) {
        val facesForDisplay = FacesForDisplay(faces)
        activity.runOnUiThread {
            setFace(view1, facesForDisplay.mainFace, false)
             setFace(view2, facesForDisplay.leftNearFace)
         }
    }

    fun clearFaces(view1: View,view2: View) {
        activity.runOnUiThread {
            view1.visibility = View.INVISIBLE
            view2.visibility = View.INVISIBLE
        }
    }

    fun setFace(
        card: View,
        face: FaceMaskDetector.DetectedFace?,
        hideIfEmpty: Boolean = true
    ) {
        if (hideIfEmpty && face == null) {
            card.visibility = View.INVISIBLE
        } else {
            card.visibility = View.VISIBLE
            val photo = card.findViewById<ImageView>(R.id.photo)
            val circle = card.findViewById<ImageView>(R.id.circle)
            val label = card.findViewById<TextView>(R.id.label)
            if (face != null) {
                photo.visibility = View.VISIBLE
                photo.setImageBitmap(face.picture)
                val color = if (face.hasMask) {
                   activity.resources.getColor(R.color.colorMaskDetected, null)
                } else {
                    activity.resources.getColor(R.color.colorNoMaskDetected, null)
                }
                circle.setColorFilter(color)
                label.text =
                    activity.resources.getString(if (face.hasMask) R.string.mask else R.string.no_mask)
            } else {
                photo.visibility = View.INVISIBLE
                circle.setColorFilter(activity.resources.getColor(R.color.colorNobody, null))
                label.text = ""
            }
        }
    }

    fun jumpToBookmark(bookmark: Bookmark?) {
        bookmark?.let {
            qiChatbot?.async()?.goToBookmark(
                it,
                AutonomousReactionImportance.HIGH,
                AutonomousReactionValidity.DELAYABLE
            )
        }
    }

    fun updateState(seesWithMask: Boolean, seesWithoutMask: Boolean, numPeople: Int) {
        val seesSomebody = seesWithMask || seesWithoutMask
        Log.w(
            TAG,
            "DBG updating state wMask=${seesWithMask} noMask=${seesWithoutMask} engaged=${engaged}"
        )

        if (engaged) {
            // Update status when already engaged
            if (seesSomebody) {
                sawNobodyCount = 0 // Stay engaged
                // See if they put on a mask
                var saidSomething = false

                // See if it's worth mentioning people putting on masks or taking them off
                if (seesWithoutMask == lastSawWithoutMask) {
                    annoyance = 0
                } else {
                    // Something changed !
                    annoyance += 1
                    if (annoyance >= 2) {
                        annoyance = 0
                        lastSawWithoutMask = seesWithoutMask
                        saidSomething = true
                        if (seesWithoutMask) {
                            if (seesWithMask) {
                                jumpToBookmark(newWithoutMaskBookmark)
                            } else {
                                jumpToBookmark(tookOffMaskBookmark)
                            }
                        } else {
                            jumpToBookmark(putOnMaskBookmark)
                        }
                        lastMentionedPeopleNum = numPeople
                        worthMentioningPeopleCounter = 0
                    }
                }

                // See if it's worth mentioning a lot of people
                if (numPeople > lastMentionedPeopleNum) {
                    if (worthMentioningPeopleCounter > 2) {
                        lastMentionedPeopleNum = numPeople
                        worthMentioningPeopleCounter = 0
                        jumpToBookmark(manyPeopleBookmark)
                    } else {
                        worthMentioningPeopleCounter += 1
                    }
                } else {
                    lastMentionedPeopleNum = numPeople
                    worthMentioningPeopleCounter = 0
                }

            } else {
                sawNobodyCount += 1
                if (sawNobodyCount > 2) {
                    engaged = false
                    //chat?.removeAllOnStartedListeners()
                    //chatFuture?.cancel(false)
                    chatFuture = null
                    lastSawWithoutMask = false
                    annoyance = 0
                }
            }
        } else if (seesSomebody) {
            engaged = true
            chat?.let { chat ->
                if (seesWithoutMask) {
                    jumpToBookmark(noMaskBookmark)
                } else {
                    jumpToBookmark(maskBookmark)
                }
                lastSawWithoutMask = seesWithoutMask
                annoyance = 0
            }
        }
    }

    fun startDetecting(view1: View,view2: View) {
        detectionFuture = detection?.start { faces ->
            // Filter and sort the faces so that they're left to right and certain enough
            val sortedFaces = faces
                .filter { (it.confidence > 0.5) }
                .sortedBy { -it.bb.left }
            Log.i(TAG, "Filtered faces ${faces.size}, ->  ${sortedFaces.size}")
            setFaces(sortedFaces,view1,view2)
            // Now update the logic
            val seesWithMask = sortedFaces.any { it.hasMask }
            val seesWithoutMask = sortedFaces.any { !it.hasMask }
            val numPeople = sortedFaces.size
            updateState(seesWithMask, seesWithoutMask, numPeople)
        }
        detectionFuture?.thenConsume {
            Log.i(
                TAG,
                "Detection future has finished: success=${it.isSuccess}, cancelled=${it.isCancelled}"
            )
            if (shouldBeRecognizing) {
                Log.w(TAG, "Stopped, but it shouldn't have - starting it again")
                startDetecting(view1,view2)
            }
        }
    }
}