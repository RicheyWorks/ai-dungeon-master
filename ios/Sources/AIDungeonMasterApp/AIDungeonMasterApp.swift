import SwiftUI

/// Optional `@main` entry for a standalone SwiftUI host (macOS / iOS app target).
/// When embedding this library in an Xcode app, call `ContentView()` from your own `@main`.
public struct AIDungeonMasterRoot: View {
    public init() {}

    public var body: some View {
        ContentView()
    }
}

#if canImport(SwiftUI) && (os(iOS) || os(macOS))
/// Demo host — available when building an app target that depends on this package.
/// Wire your Xcode app's entry point to `AIDungeonMasterRoot` or `ContentView`.
@available(iOS 16.0, macOS 13.0, *)
public enum AIDungeonMasterAppEntry {
    @MainActor
    public static func rootView() -> some View {
        AIDungeonMasterRoot()
    }
}
#endif
