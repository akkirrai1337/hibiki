import UIKit
import shared

@main
final class HibikiAppDelegate: UIResponder, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        true
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(
        _ scene: UIScene,
        willConnectTo session: UISceneSession,
        options connectionOptions: UIScene.ConnectionOptions
    ) {
        guard let windowScene = scene as? UIWindowScene else {
            return
        }

        let window = UIWindow(windowScene: windowScene)
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
    }
}
