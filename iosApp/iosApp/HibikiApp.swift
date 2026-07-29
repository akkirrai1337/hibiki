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
        window.rootViewController = MainViewControllerKt.MainViewController(
            systemLanguage: Locale.current.languageCode ?? "en"
        )
        window.makeKeyAndVisible()
        self.window = window
        return true
    }
}
