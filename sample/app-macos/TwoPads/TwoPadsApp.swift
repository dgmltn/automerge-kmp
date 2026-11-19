import SwiftUI

@main
struct TwoPadsApp: App {
    @StateObject private var model = TwoPadsModel()

    var body: some Scene {
        WindowGroup {
            TwoPadsView(model: model)
        }
        .defaultSize(width: 900, height: 360)
    }
}
