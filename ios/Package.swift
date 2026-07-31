// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "AIDungeonMasterApp",
    platforms: [
        .iOS(.v16),
        .macOS(.v13),
    ],
    products: [
        .library(
            name: "AIDungeonMasterApp",
            targets: ["AIDungeonMasterApp"]
        ),
    ],
    dependencies: [
        .package(name: "AIDungeonMasterClient", path: "../clients/swift"),
    ],
    targets: [
        .target(
            name: "AIDungeonMasterApp",
            dependencies: [
                .product(name: "AIDungeonMasterClient", package: "AIDungeonMasterClient"),
            ],
            path: "Sources/AIDungeonMasterApp"
        ),
    ]
)
