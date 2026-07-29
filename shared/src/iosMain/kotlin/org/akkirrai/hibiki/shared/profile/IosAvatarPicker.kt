package org.akkirrai.hibiki.shared.profile

import platform.Foundation.NSURL
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerImageURL
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** Host-owned iOS image picker used by the shared profile screen. */
internal class IosAvatarPicker {
    private var delegate: Delegate? = null

    fun present(from: UIViewController, onPicked: (String) -> Unit) {
        val picker = UIImagePickerController()
        val pickerDelegate = Delegate(
            onPicked = { uri ->
                delegate = null
                from.dismissViewControllerAnimated(true, completion = null)
                onPicked(uri)
            },
            onCancelled = {
                delegate = null
                from.dismissViewControllerAnimated(true, completion = null)
            },
        )
        delegate = pickerDelegate
        picker.delegate = pickerDelegate
        picker.sourceType = platform.UIKit.UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        from.presentViewController(picker, animated = true, completion = null)
    }

    private class Delegate(
        private val onPicked: (String) -> Unit,
        private val onCancelled: () -> Unit,
    ) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun imagePickerController(
            picker: UIImagePickerController,
            didFinishPickingMediaWithInfo: Map<Any?, *>,
        ) {
            val url = didFinishPickingMediaWithInfo
                ?.get(UIImagePickerControllerImageURL)
                ?.let { it as? NSURL }
                ?.absoluteString
            if (url != null) {
                onPicked(url)
            } else if (didFinishPickingMediaWithInfo?.get(UIImagePickerControllerOriginalImage) != null) {
                picker.dismissViewControllerAnimated(true, completion = null)
            }
        }

        override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
            onCancelled()
        }
    }
}
