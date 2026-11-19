import CoreGraphics
import Foundation

let pid = Int(CommandLine.arguments[1])!
let windows = CGWindowListCopyWindowInfo([.optionOnScreenOnly, .excludeDesktopElements], kCGNullWindowID) as? [[String: Any]] ?? []
for window in windows where (window[kCGWindowOwnerPID as String] as? Int) == pid {
    let layer = window[kCGWindowLayer as String] as? Int
    let bounds = window[kCGWindowBounds as String] as? [String: CGFloat]
    if layer == 0, (bounds?["Width"] ?? 0) >= 400, (bounds?["Height"] ?? 0) >= 300,
       let number = window[kCGWindowNumber as String] as? Int {
        print(number)
        exit(0)
    }
}
exit(1)
