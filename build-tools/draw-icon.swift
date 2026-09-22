import AppKit

let size = 1024.0
let image = NSImage(size: NSSize(width: size, height: size))
image.lockFocus()
guard let ctx = NSGraphicsContext.current?.cgContext else { exit(1) }

// 배경: 둥근 사각형 + 대각선 그라데이션
let bg = CGPath(roundedRect: CGRect(x: 0, y: 0, width: size, height: size), cornerWidth: 230, cornerHeight: 230, transform: nil)
ctx.addPath(bg); ctx.clip()
let colors = [CGColor(red: 0.36, green: 0.42, blue: 0.95, alpha: 1), CGColor(red: 0.20, green: 0.24, blue: 0.72, alpha: 1)] as CFArray
let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1])!
ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size), end: CGPoint(x: size, y: 0), options: [])

// 스위치 세 개: 위 두 개는 켜짐(오른쪽), 아래는 꺼짐(왼쪽)
func drawSwitch(y: CGFloat, on: Bool) {
    let track = CGRect(x: 232, y: y, width: 560, height: 176)
    ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: on ? 0.95 : 0.35))
    ctx.addPath(CGPath(roundedRect: track, cornerWidth: 88, cornerHeight: 88, transform: nil)); ctx.fillPath()
    let knobX = on ? track.maxX - 160 : track.minX + 16
    ctx.setFillColor(on ? CGColor(red: 0.28, green: 0.33, blue: 0.85, alpha: 1) : CGColor(red: 1, green: 1, blue: 1, alpha: 0.9))
    ctx.fillEllipse(in: CGRect(x: knobX, y: y + 16, width: 144, height: 144))
}
drawSwitch(y: 660, on: true)
drawSwitch(y: 424, on: true)
drawSwitch(y: 188, on: false)
image.unlockFocus()

let rep = NSBitmapImageRep(data: image.tiffRepresentation!)!
let png = rep.representation(using: .png, properties: [:])!
try! png.write(to: URL(fileURLWithPath: CommandLine.arguments[1]))
