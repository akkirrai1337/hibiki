import UIKit
import AVFAudio
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
        let composeViewController = MainViewControllerKt.MainViewController(
            systemLanguage: Locale.current.languageCode ?? "en"
        )
        let rootViewController = PlayerOrientationViewController(
            contentViewController: composeViewController
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

private let playerOrientationNotification = Notification.Name(
    "org.akkirrai.hibiki.player.orientation"
)

private final class PlayerOrientationViewController: UIViewController {
    private let contentViewController: UIViewController
    private var playerIsActive = false
    private var orientationObserver: NSObjectProtocol?

    init(contentViewController: UIViewController) {
        self.contentViewController = contentViewController
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(contentViewController)
        contentViewController.view.frame = view.bounds
        contentViewController.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(contentViewController.view)
        contentViewController.didMove(toParent: self)

        orientationObserver = NotificationCenter.default.addObserver(
            forName: playerOrientationNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let active = (notification.object as? NSNumber)?.boolValue else { return }
            self?.setPlayerActive(active)
        }
    }

    override var shouldAutorotate: Bool { true }

    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        playerIsActive ? .landscape : .allButUpsideDown
    }

    private func setPlayerActive(_ active: Bool) {
        guard playerIsActive != active else { return }
        playerIsActive = active
        configurePlayerAudioSession(active: active)
        setNeedsUpdateOfSupportedInterfaceOrientations()
        UIViewController.attemptRotationToDeviceOrientation()

        guard #available(iOS 16.0, *), let windowScene = view.window?.windowScene else { return }
        let preferences = UIWindowScene.GeometryPreferences.iOS(
            interfaceOrientations: active ? .landscape : .allButUpsideDown
        )
        windowScene.requestGeometryUpdate(preferences) { error in
            print("Unable to update player orientation: \(error.localizedDescription)")
        }
    }

    private func configurePlayerAudioSession(active: Bool) {
        do {
            let audioSession = AVAudioSession.sharedInstance()
            guard active else {
                try audioSession.setActive(false, options: .notifyOthersOnDeactivation)
                return
            }
            try audioSession.setCategory(.playback, mode: .moviePlayback)
            try audioSession.setActive(true)
        } catch {
            print("Unable to configure player audio session: \(error.localizedDescription)")
        }
    }

    deinit {
        orientationObserver.map(NotificationCenter.default.removeObserver)
    }
}
