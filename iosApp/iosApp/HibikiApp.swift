import UIKit
import shared

@main
final class HibikiAppDelegate: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        let window = UIWindow(frame: UIScreen.main.bounds)
        let rootViewController = MainViewControllerKt.MainViewController(
            systemLanguage: Locale.current.languageCode ?? "en"
        )
        rootViewController.modalPresentationStyle = .fullScreen
        rootViewController.edgesForExtendedLayout = [.top, .bottom, .left, .right]
        rootViewController.extendedLayoutIncludesOpaqueBars = true
        rootViewController.view.backgroundColor = .systemBackground
        window.rootViewController = rootViewController
        window.backgroundColor = .systemBackground
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
