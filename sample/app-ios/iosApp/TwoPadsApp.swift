import SwiftUI
import TwoPadsUI

@main
struct TwoPadsApp: App {
    var body: some Scene {
        WindowGroup {
            // Compose pads the screen for the safe area and the keyboard itself.
            ComposeView().ignoresSafeArea()
        }
    }
}

/// Hosts the shared Compose screen from :shared-ui; everything on screen is Compose.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
