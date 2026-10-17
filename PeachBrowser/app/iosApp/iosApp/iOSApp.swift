import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinKt.doInitKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onOpenURL { url in
                    if url.isFileURL {
                        // Security-scoped access for local files (AirDrop, Files app, Mail)
                        guard url.startAccessingSecurityScopedResource() else { return }
                        defer { url.stopAccessingSecurityScopedResource() }
                        ExternalLinkRouter.shared.handleIncomingUrl(rawUri: url.absoluteString)
                    } else {
                        ExternalLinkRouter.shared.handleIncomingUrl(rawUri: url.absoluteString)
                    }
                }
        }
    }
}