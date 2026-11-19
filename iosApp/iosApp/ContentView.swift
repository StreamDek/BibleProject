import SwiftUI
import UIKit
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Mirrors the reader's Immersive view setting so SwiftUI can hide the status bar.
final class StatusBarState: ObservableObject {
    @Published var hidden = false
}

struct ContentView: View {
    @StateObject private var statusBar = StatusBarState()

    var body: some View {
        ComposeView()
            .ignoresSafeArea()
            .statusBarHidden(statusBar.hidden)
            .animation(.easeInOut(duration: 0.2), value: statusBar.hidden)
            .onAppear {
                let state = statusBar
                MainViewControllerKt.setStatusBarHiddenListener { hidden in
                    DispatchQueue.main.async { state.hidden = hidden.boolValue }
                }
            }
    }
}
