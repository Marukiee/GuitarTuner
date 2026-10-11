// Draws the Guitar Tuner app icon: a guitar pick in front of the pitch line, on the same
// indigo plate as Tandem and Rushes.
// Usage: swift scripts/make-icon.swift <output.png> [size]
//        swift scripts/make-icon.swift --android   (prints the adaptive icon vectors)
import AppKit

let arguments = CommandLine.arguments
let androidMode = arguments.count >= 2 && arguments[1] == "--android"
guard androidMode || arguments.count >= 2 else {
    FileHandle.standardError.write(Data("usage: make-icon.swift <output.png> [size] | --android\n".utf8))
    exit(1)
}
let size = (!androidMode && arguments.count > 2) ? CGFloat(Double(arguments[2]) ?? 1024) : 1024

func color(_ hex: UInt32, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(
        srgbRed: CGFloat((hex >> 16) & 0xFF) / 255,
        green: CGFloat((hex >> 8) & 0xFF) / 255,
        blue: CGFloat(hex & 0xFF) / 255,
        alpha: alpha
    )
}

// MARK: shapes (1024 point canvas, y up)

/// A convex polygon swollen by a radius: the corners are arcs, the edges are the polygon's
/// edges pushed outwards. Growing the radius by a gap is therefore an exact outline of the
/// shape with a margin around it, which is what the single colour icon needs.
func swollen(_ centres: [CGPoint], radius: CGFloat) -> CGPath {
    let path = CGMutablePath()
    let n = centres.count
    // Counter clockwise polygon: the outward normal of an edge is its direction turned right.
    func normal(_ a: CGPoint, _ b: CGPoint) -> CGPoint {
        let dx = b.x - a.x, dy = b.y - a.y
        let len = (dx * dx + dy * dy).squareRoot()
        return CGPoint(x: dy / len, y: -dx / len)
    }
    for i in 0 ..< n {
        let prev = centres[(i + n - 1) % n], cur = centres[i], next = centres[(i + 1) % n]
        let n1 = normal(prev, cur), n2 = normal(cur, next)
        let start = CGPoint(x: cur.x + n1.x * radius, y: cur.y + n1.y * radius)
        if i == 0 { path.move(to: start) } else { path.addLine(to: start) }
        let a1 = atan2(n1.y, n1.x)
        var a2 = atan2(n2.y, n2.x)
        while a2 < a1 { a2 += 2 * .pi }
        path.addArc(center: cur, radius: radius, startAngle: a1, endAngle: a2, clockwise: false)
    }
    path.closeSubpath()
    return path
}

func place(_ path: CGPath, at center: CGPoint, degrees: CGFloat) -> CGPath {
    let b = path.boundingBoxOfPath
    var t = CGAffineTransform(translationX: center.x, y: center.y)
    t = t.rotated(by: degrees * .pi / 180)
    t = t.translatedBy(x: -b.midX, y: -b.midY)
    return path.copy(using: &t)!
}

struct Shape { var path: CGPath; var fill: [UInt32] }

// Pitch line: a tall pill, upright, behind the pick.
let linePath = place(CGPath(roundedRect: CGRect(x: 0, y: 0, width: 136, height: 600), cornerWidth: 68, cornerHeight: 68, transform: nil),
                     at: CGPoint(x: 689, y: 520), degrees: 0)
// Guitar pick: a fat triangle pointing down, every corner generously round.
func pickShape(extra: CGFloat) -> CGPath {
    // Counter clockwise: top left, tip (bottom), top right.
    let c = [CGPoint(x: 0, y: 150), CGPoint(x: 120, y: 0), CGPoint(x: 240, y: 150)]
    return place(swollen(c, radius: 100 + extra), at: CGPoint(x: 487, y: 504), degrees: 0)
}
let pickPath = pickShape(extra: 0)

if androidMode {
    // MARK: Android vector output, 108 viewport, the visible 72 stands for the 824 plate.
    let s: CGFloat = 72.0 / 824.0
    func vd(_ p: CGPath) -> String {
        var t = CGAffineTransform(a: s, b: 0, c: 0, d: -s, tx: 54 - 512 * s, ty: 54 + 512 * s)
        let q = p.copy(using: &t)!
        var out = ""
        func f(_ v: CGFloat) -> String { String(format: "%.2f", v).replacingOccurrences(of: ".00", with: "") }
        q.applyWithBlock { e in
            let pts = e.pointee.points
            switch e.pointee.type {
            case .moveToPoint: out += "M\(f(pts[0].x)),\(f(pts[0].y))"
            case .addLineToPoint: out += "L\(f(pts[0].x)),\(f(pts[0].y))"
            case .addQuadCurveToPoint: out += "Q\(f(pts[0].x)),\(f(pts[0].y)) \(f(pts[1].x)),\(f(pts[1].y))"
            case .addCurveToPoint: out += "C\(f(pts[0].x)),\(f(pts[0].y)) \(f(pts[1].x)),\(f(pts[1].y)) \(f(pts[2].x)),\(f(pts[2].y))"
            case .closeSubpath: out += "Z"
            @unknown default: break
            }
        }
        return out
    }
    func bounds(_ p: CGPath) -> CGRect {
        var t = CGAffineTransform(a: s, b: 0, c: 0, d: -s, tx: 54 - 512 * s, ty: 54 + 512 * s)
        return p.copy(using: &t)!.boundingBoxOfPath
    }
    let lb = bounds(linePath), pb = bounds(pickPath)
    func gradient(_ b: CGRect, _ top: String, _ bottom: String) -> String {
        """
                <aapt:attr name="android:fillColor">
                    <gradient android:startX="54" android:startY="\(String(format: "%.2f", b.maxY))" android:endX="54" android:endY="\(String(format: "%.2f", b.minY))" android:type="linear">
                        <item android:offset="0" android:color="#\(bottom)" />
                        <item android:offset="1" android:color="#\(top)" />
                    </gradient>
                </aapt:attr>
        """
    }
    let kind = arguments.count > 2 ? arguments[2] : "foreground"
    if kind == "foreground" {
        print("""
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:aapt="http://schemas.android.com/aapt"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <!-- The same plate geometry as the Mac icons: the visible 72dp of an adaptive icon stands for
                 the 824 point plate. The pitch line is behind, the pick in front, each with a soft shadow. -->
            <group android:translateY="1.6">
                <path android:fillColor="#000000" android:fillAlpha="0.22" android:pathData="\(vd(linePath))" />
            </group>
            <path android:pathData="\(vd(linePath))">
        \(gradient(lb, "E8779F", "FFB0CB"))
            </path>
            <group android:translateY="1.6">
                <path android:fillColor="#000000" android:fillAlpha="0.22" android:pathData="\(vd(pickPath))" />
            </group>
            <path android:pathData="\(vd(pickPath))">
        \(gradient(pb, "C9C8FA", "FFFFFF"))
            </path>
        </vector>
        """)
    } else {
        let gap = pickShape(extra: 14)
        print("""
        <?xml version="1.0" encoding="utf-8"?>
        <vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="108dp"
            android:height="108dp"
            android:viewportWidth="108"
            android:viewportHeight="108">
            <!-- One tint, two shapes: a thin gap is cut round the pick so the pitch line stays apart from it. -->
            <group>
                <clip-path android:pathData="M0,0H108V108H0Z\(vd(gap))" />
                <path android:fillColor="#FF000000" android:pathData="\(vd(linePath))" />
            </group>
            <path android:fillColor="#FF000000" android:pathData="\(vd(pickPath))" />
        </vector>
        """)
    }
    exit(0)
}

// MARK: PNG

let bitmap = NSBitmapImageRep(
    bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size),
    bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false,
    colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0
)!
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
let context = NSGraphicsContext.current!.cgContext
context.scaleBy(x: size / 1024, y: size / 1024)

let plate = NSRect(x: 100, y: 100, width: 824, height: 824)
let platePath = NSBezierPath(roundedRect: plate, xRadius: 186, yRadius: 186)

context.saveGState()
let shadow = NSShadow()
shadow.shadowColor = NSColor.black.withAlphaComponent(0.35)
shadow.shadowOffset = NSSize(width: 0, height: -14)
shadow.shadowBlurRadius = 28
shadow.set()
color(0x2E2C6B).setFill()
platePath.fill()
context.restoreGState()

context.saveGState()
platePath.addClip()
NSGradient(colors: [color(0x7A79EE), color(0x4A49C4), color(0x2E2C6B)], atLocations: [0, 0.55, 1], colorSpace: .sRGB)!
    .draw(in: plate, angle: -60)
NSGradient(colors: [NSColor.white.withAlphaComponent(0.22), NSColor.white.withAlphaComponent(0)], atLocations: [0, 1], colorSpace: .sRGB)!
    .draw(in: NSRect(x: plate.minX, y: plate.midY, width: plate.width, height: plate.height / 2), angle: -90)
context.restoreGState()

func lift(_ path: CGPath, fill: [NSColor]) {
    let bez = NSBezierPath(cgPath: path)
    context.saveGState()
    let glow = NSShadow()
    glow.shadowColor = NSColor.black.withAlphaComponent(0.28)
    glow.shadowOffset = NSSize(width: 0, height: -10)
    glow.shadowBlurRadius = 22
    glow.set()
    fill[0].setFill()
    bez.fill()
    context.setShadow(offset: .zero, blur: 0)
    bez.addClip()
    let bounds = bez.bounds
    NSGradient(colors: fill)!.draw(in: bounds, angle: 90)
    NSGradient(colors: [NSColor.white.withAlphaComponent(0.35), NSColor.white.withAlphaComponent(0)])!
        .draw(in: NSRect(x: bounds.minX, y: bounds.midY, width: bounds.width, height: bounds.height / 2), angle: -90)
    context.restoreGState()
}

lift(linePath, fill: [color(0xFFB0CB), color(0xE8779F)])
lift(pickPath, fill: [color(0xFFFFFF), color(0xC9C8FA)])

NSGraphicsContext.restoreGraphicsState()
let png = bitmap.representation(using: .png, properties: [:])!
try png.write(to: URL(fileURLWithPath: arguments[1]))
