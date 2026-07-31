package org.akkirrai.hibiki.shared.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIViewController
import platform.darwin.NSObject

/** UIKit edge-pan bridge used by the common back handler on iOS. */
@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
internal object IosBackBridge {
    private var handler: (() -> Unit)? = null
    private var gestureTarget: IosBackGestureTarget? = null

    fun install(viewController: UIViewController) {
        if (gestureTarget != null) return
        val target = IosBackGestureTarget { handler?.invoke() }
        val gesture = UIScreenEdgePanGestureRecognizer(
            target = target,
            action = NSSelectorFromString("handleEdgePan:"),
        )
        gesture.edges = UIRectEdgeLeft
        viewController.view.addGestureRecognizer(gesture)
        gestureTarget = target
    }

    fun update(enabled: Boolean, onBack: () -> Unit) {
        handler = onBack.takeIf { enabled }
    }
}

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
private class IosBackGestureTarget(
    private val onEnded: () -> Unit,
) : NSObject() {
    @ObjCAction
    fun handleEdgePan(gesture: UIGestureRecognizer) {
        if (gesture.state == UIGestureRecognizerStateEnded) onEnded()
    }
}
