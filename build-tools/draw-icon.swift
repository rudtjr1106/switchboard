// 앱 아이콘과 GitHub 용 이미지를 정확한 픽셀 크기로 그린다
// 사용: swift build-tools/draw-icon.swift <icon|social> <출력.png> [픽셀 크기]
import AppKit

func bitmap(_ width: Int, _ height: Int, draw: (CGContext) -> Void) -> Data {
    let rep = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: width, pixelsHigh: height, bitsPerSample: 8,
                               samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB,
                               bytesPerRow: 0, bitsPerPixel: 0)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: rep)
    draw(NSGraphicsContext.current!.cgContext)
    NSGraphicsContext.restoreGraphicsState()
    return rep.representation(using: .png, properties: [:])!
}

let indigoTop = CGColor(red: 0.36, green: 0.42, blue: 0.95, alpha: 1)
let indigoBottom = CGColor(red: 0.20, green: 0.24, blue: 0.72, alpha: 1)

/// 1024 기준 좌표로 그린 아이콘을 (x, y, size) 자리에 그린다
func drawIcon(_ ctx: CGContext, x: CGFloat, y: CGFloat, size: CGFloat) {
    ctx.saveGState()
    ctx.translateBy(x: x, y: y)
    ctx.scaleBy(x: size / 1024, y: size / 1024)
    let bg = CGPath(roundedRect: CGRect(x: 0, y: 0, width: 1024, height: 1024), cornerWidth: 230, cornerHeight: 230, transform: nil)
    ctx.addPath(bg); ctx.clip()
    let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [indigoTop, indigoBottom] as CFArray, locations: [0, 1])!
    ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 1024), end: CGPoint(x: 1024, y: 0), options: [])
    func toggle(_ ty: CGFloat, _ on: Bool) {
        let track = CGRect(x: 232, y: ty, width: 560, height: 176)
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: on ? 0.95 : 0.35))
        ctx.addPath(CGPath(roundedRect: track, cornerWidth: 88, cornerHeight: 88, transform: nil)); ctx.fillPath()
        let knobX = on ? track.maxX - 160 : track.minX + 16
        ctx.setFillColor(on ? CGColor(red: 0.28, green: 0.33, blue: 0.85, alpha: 1) : CGColor(red: 1, green: 1, blue: 1, alpha: 0.9))
        ctx.fillEllipse(in: CGRect(x: knobX, y: ty + 16, width: 144, height: 144))
    }
    toggle(660, true); toggle(424, true); toggle(188, false)
    ctx.restoreGState()
}

func text(_ ctx: CGContext, _ string: String, size: CGFloat, weight: NSFont.Weight, alpha: CGFloat, at point: CGPoint) {
    let attrs: [NSAttributedString.Key: Any] = [
        .font: NSFont.systemFont(ofSize: size, weight: weight),
        .foregroundColor: NSColor(white: 1, alpha: alpha),
    ]
    NSAttributedString(string: string, attributes: attrs).draw(at: point)
}

let args = CommandLine.arguments
let out = URL(fileURLWithPath: args[2])
switch args[1] {
case "icon":
    let px = Int(args.count > 3 ? args[3] : "1024")!
    try! bitmap(px, px) { drawIcon($0, x: 0, y: 0, size: CGFloat(px)) }.write(to: out)
case "social":
    // GitHub 저장소 소셜 미리보기 권장 크기 1280×640
    try! bitmap(1280, 640) { ctx in
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [indigoTop, indigoBottom] as CFArray, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: 640), end: CGPoint(x: 1280, y: 0), options: [])
        ctx.setShadow(offset: CGSize(width: 0, height: -12), blur: 40, color: CGColor(red: 0, green: 0, blue: 0.2, alpha: 0.35))
        drawIcon(ctx, x: 120, y: 180, size: 280)
        ctx.setShadow(offset: .zero, blur: 0, color: nil)
        text(ctx, "스위치보드", size: 104, weight: .bold, alpha: 1, at: CGPoint(x: 470, y: 318))
        text(ctx, "Android 원격 설정 편집기", size: 40, weight: .semibold, alpha: 0.92, at: CGPoint(x: 476, y: 248))
        text(ctx, "macOS · Windows · 온디바이스 AI", size: 30, weight: .regular, alpha: 0.75, at: CGPoint(x: 476, y: 196))
    }.write(to: out)
default:
    fatalError("icon 또는 social")
}
