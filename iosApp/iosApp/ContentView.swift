import SwiftUI
import UIKit
import shared

struct ComposeViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            systemLanguage: Locale.current.languageCode ?? "en"
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeViewController()
            .ignoresSafeArea(.keyboard)
    }
}
