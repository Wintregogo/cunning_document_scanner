package biz.cunning.cunning_document_scanner.fallback

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import biz.cunning.cunning_document_scanner.R
import biz.cunning.cunning_document_scanner.fallback.constants.DefaultSetting
import biz.cunning.cunning_document_scanner.fallback.constants.DocumentScannerExtra
import biz.cunning.cunning_document_scanner.fallback.extensions.move
import biz.cunning.cunning_document_scanner.fallback.extensions.onClick
import biz.cunning.cunning_document_scanner.fallback.extensions.saveToFile
import biz.cunning.cunning_document_scanner.fallback.extensions.screenHeight
import biz.cunning.cunning_document_scanner.fallback.extensions.screenWidth
import biz.cunning.cunning_document_scanner.fallback.models.Document
import biz.cunning.cunning_document_scanner.fallback.models.Point
import biz.cunning.cunning_document_scanner.fallback.models.Quad
import biz.cunning.cunning_document_scanner.fallback.ui.ImageCropView
import biz.cunning.cunning_document_scanner.fallback.utils.CameraUtil
import biz.cunning.cunning_document_scanner.fallback.utils.FileUtil
import biz.cunning.cunning_document_scanner.fallback.utils.ImageUtil
import java.io.File
/**
 * This class contains the main document scanner code. It opens the camera, lets the user
 * take a photo of a document (homework paper, business card, etc.), detects document corners,
 * allows user to make adjustments to the detected corners, depending on options, and saves
 * the cropped document. It allows the user to do this for 1 or more documents.
 *
 * @constructor creates document scanner activity
 */
class DocumentScannerActivity : AppCompatActivity() {
    /**
     * @property maxNumDocuments maximum number of documents a user can scan at a time
     */
    private var maxNumDocuments = DefaultSetting.MAX_NUM_DOCUMENTS

    /**
     * @property croppedImageQuality the 0 - 100 quality of the cropped image
     */
    private var croppedImageQuality = DefaultSetting.CROPPED_IMAGE_QUALITY

    /**
     * @property isSingleScanMode 是否为单次扫描模式，若为true则拍照后直接进入编辑界面，接受裁剪后直接返回结果
     */
    private var isSingleScanMode = false

    /**
     * @property cropperOffsetWhenCornersNotFound if we can't find document corners, we set
     * corners to image size with a slight margin
     */
    private val cropperOffsetWhenCornersNotFound = 100.0

    /**
     * @property document This is the current document. Initially it's null. Once we capture
     * the photo, and find the corners we update document.
     */
    private var document: Document? = null

    /**
     * @property documents a list of documents (original photo file path, original photo
     * dimensions and 4 corner points)
     */
    private val documents = mutableListOf<Document>()

    /**
     * @property cameraUtil gets called with photo file path once user takes photo, or
     * exits camera
     */
    private val cameraUtil = CameraUtil(
        this,
        onPhotoCaptureSuccess = {
            // user takes photo
            originalPhotoPath ->

            // if maxNumDocuments is 3 and this is the 3rd photo, hide the new photo button since
            // we reach the allowed limit
            if (documents.size == maxNumDocuments - 1) {
                val newPhotoButton: ImageButton = findViewById(R.id.new_photo_button)
                newPhotoButton.isClickable = false
                newPhotoButton.visibility = View.INVISIBLE
            }

            // get bitmap from photo file path
            val photo: Bitmap? = try {
                ImageUtil().getImageFromFilePath(originalPhotoPath)
            } catch (exception: Exception) {
                finishIntentWithError("Unable to get bitmap: ${exception.localizedMessage}")
                return@CameraUtil
            }

            if (photo == null) {
                finishIntentWithError("Document bitmap is null.")
                return@CameraUtil
            }

            // get document corners by detecting them, or falling back to photo corners with
            // slight margin if we can't find the corners
            val corners = try {
                val (topLeft, topRight, bottomLeft, bottomRight) = getDocumentCorners(photo)
                Quad(topLeft, topRight, bottomRight, bottomLeft)
            } catch (exception: Exception) {
                finishIntentWithError(
                    "unable to get document corners: ${exception.message}"
                )
                return@CameraUtil
            }

            document = Document(originalPhotoPath, photo.width, photo.height, corners)


            // user is allowed to move corners to make corrections
            try {
                // set preview image height based off of photo dimensions
                imageView.setImagePreviewBounds(photo, screenWidth, screenHeight)

                // display original photo, so user can adjust detected corners
                imageView.setImage(photo)

                // document corner points are in original image coordinates, so we need to
                // scale and move the points to account for blank space (caused by photo and
                // photo container having different aspect ratios)
                val cornersInImagePreviewCoordinates = corners
                    .mapOriginalToPreviewImageCoordinates(
                        imageView.imagePreviewBounds,
                        imageView.imagePreviewBounds.height() / photo.height
                    )

                // display cropper, and allow user to move corners
                imageView.setCropper(cornersInImagePreviewCoordinates)
                
                // 如果是单次扫描模式，自动接受裁剪
                if (isSingleScanMode) {
                    // 短暂延迟以让用户看到检测到的边缘
                    imageView.postDelayed({
                        onClickAccept()
                    }, 500) // 500毫秒延迟
                }
            } catch (exception: Exception) {
                finishIntentWithError(
                    "unable get image preview ready: ${exception.message}"
                )
                return@CameraUtil
            }
        },
        onCancelPhoto = {
            // user exits camera
            // complete document scan if this is the first document since we can't go to crop view
            // until user takes at least 1 photo
            if (documents.isEmpty()) {
                onClickCancel()
            }
        }
    )

    /**
     * @property imageView container with original photo and cropper
     */
    private lateinit var imageView: ImageCropView

    /**
     * called when activity is created
     *
     * @param savedInstanceState persisted data that maintains state
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show cropper, accept crop button, add new document button, and
        // retake photo button. Since we open the camera in a few lines, the user
        // doesn't see this until they finish taking a photo
        setContentView(R.layout.activity_image_crop)
        imageView = findViewById(R.id.image_view)

        try {
            // validate maxNumDocuments option, and update default if user sets it
            var userSpecifiedMaxImages: Int? = null
            intent.extras?.get(DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS)?.let {
                if (it.toString().toIntOrNull() == null) {
                    throw Exception(
                        "${DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS} must be a positive number"
                    )
                }
                userSpecifiedMaxImages = it as Int
                maxNumDocuments = userSpecifiedMaxImages as Int
            }

            // validate croppedImageQuality option, and update value if user sets it
            intent.extras?.get(DocumentScannerExtra.EXTRA_CROPPED_IMAGE_QUALITY)?.let {
                if (it !is Int || it < 0 || it > 100) {
                    throw Exception(
                        "${DocumentScannerExtra.EXTRA_CROPPED_IMAGE_QUALITY} must be a number " +
                                "between 0 and 100"
                    )
                }
                croppedImageQuality = it
            }
            
            // 获取单次扫描模式设置
            intent.extras?.get(DocumentScannerExtra.EXTRA_SINGLE_SCAN_MODE)?.let {
                if (it !is Boolean) {
                    throw Exception(
                        "${DocumentScannerExtra.EXTRA_SINGLE_SCAN_MODE} must be a boolean"
                    )
                }
                isSingleScanMode = it
            }
            
        } catch (exception: Exception) {
            finishIntentWithError(
                "invalid extra: ${exception.message}"
            )
            return
        }

        // set click event handlers for new document button, accept and crop document button,
        // and retake document photo button
        val newPhotoButton: ImageButton = findViewById(R.id.new_photo_button)
        val completeDocumentScanButton: ImageButton = findViewById(
            R.id.complete_document_scan_button
        )
        val retakePhotoButton: ImageButton = findViewById(R.id.retake_photo_button)
        
        // 在单次扫描模式下隐藏"新增照片"按钮
        if (isSingleScanMode) {
            newPhotoButton.visibility = View.GONE
        }

        newPhotoButton.onClick {
            document?.let {
                documents.add(it)
            }
            document = null
            imageView.setImage(null)
            imageView.setCropper(null)
            openCamera()
        }

        completeDocumentScanButton.onClick {
            document?.let {
                documents.add(it)
            }
            cropDocuments()
        }

        retakePhotoButton.onClick {
            document = null
            imageView.setImage(null)
            imageView.setCropper(null)
            openCamera()
        }

        // open the camera
        openCamera()
    }

    /**
     * finishes the document scan and returns a result
     */
    private fun cropDocuments() {
        if (documents.size == 0) {
            // user didn't accept a document yet
            setResult(
                Activity.RESULT_CANCELED,
                Intent()
            )
            finish()
            return
        }

        // crop documents, save them to external storage, and create array of cropped documents
        val croppedImageResults: ArrayList<String> = ArrayList()
        for (document in documents) {
            var croppedImageResult: String? = null
            try {
                val inputBitmap = ImageUtil().getImageFromFilePath(document.originalPhotoFilePath)
                    ?: throw Exception("Document bitmap is null.")

                // crop document image using corners
                val outputBitmap = document.corners.cropAndWarpImage(inputBitmap)

                // save cropped image in external storage
                croppedImageResult = outputBitmap.saveToFile(
                    this,
                    "crop_" + FileUtil().getImageFilename(),
                    croppedImageQuality,
                )
            } catch (exception: Exception) {
                finishIntentWithError(
                    "Failed to crop document: ${exception.message}"
                )
                return
            }

            if (croppedImageResult == null) {
                finishIntentWithError("Failed to crop document.")
                return
            }

            croppedImageResults.add(croppedImageResult)
        }

        val resultIntent = Intent()
        resultIntent.putStringArrayListExtra("croppedImageResults", croppedImageResults)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /**
     * gets document corners from the bitmap provided
     *
     * @param photo the image to get corners from
     * @return a list of corners in correct order (top left, top right, bottom right, bottom left)
     */
    private fun getDocumentCorners(photo: Bitmap): List<Point> {
        try {
            val cornerPoints = Quad.findDocumentCorners(this, photo)
            if (!cornerPoints.corners.isEmpty()) {
                return cornerPoints.corners
            }
        } catch (exception: Exception) {
            throw Exception("Error finding document corners: ${exception.message}")
        }

        // if we reached this point, finding corners using EdgeDetection failed,
        // so let's just set corners to the corners of the photo, with a margin of
        // cropperOffsetWhenCornersNotFound
        return listOf(
            Point(cropperOffsetWhenCornersNotFound, cropperOffsetWhenCornersNotFound),
            Point(
                photo.width.toDouble() - cropperOffsetWhenCornersNotFound,
                cropperOffsetWhenCornersNotFound
            ),
            Point(
                photo.width.toDouble() - cropperOffsetWhenCornersNotFound,
                photo.height.toDouble() - cropperOffsetWhenCornersNotFound
            ),
            Point(
                cropperOffsetWhenCornersNotFound,
                photo.height.toDouble() - cropperOffsetWhenCornersNotFound
            )
        )
    }

    /**
     * opens the camera via the camera util, which returns a photo with onPhotoCaptureSuccess
     * or no photo if user cancels with onCancelPhoto
     */
    private fun openCamera() {
        try {
            cameraUtil.openCamera()
        } catch (exception: Exception) {
            finishIntentWithError(
                "Error opening camera: ${exception.message}"
            )
        }
    }

    /**
     * handles accept button click
     */
    private fun onClickAccept() {
        // if this is the first photo, and there is no "add new photo" option, then
        // user is likely just trying to complete the scan. So call cropDocuments which
        // handles one or more photos, and multiple cropped document results
        document?.let {
            documents.add(it)
            if (documents.size >= maxNumDocuments || isSingleScanMode) {
                cropDocuments()
                return
            }
        }

        document = null
        imageView.setImage(null)
        imageView.setCropper(null)
        openCamera()
    }

    /**
     * handles cancel button click
     */
    private fun onClickCancel() {
        setResult(
            Activity.RESULT_CANCELED,
            Intent()
        )
        finish()
    }

    /**
     * returns an ERROR result with message, and finishes activity
     *
     * @param error the error message to return with the ERROR result
     */
    private fun finishIntentWithError(error: String) {
        val resultIntent = Intent()
        resultIntent.putExtra("error", error)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}