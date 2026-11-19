import Foundation
import TwoPadsKit

/// What one pad shows: its text and selection, and how big its document is.
///
/// The selection is an `NSRange`, counted in UTF-16 units as `NSTextView` and Kotlin's `String` both
/// count, never in Swift `Character`s: an emoji is one `Character` but two units.
struct PadState: Equatable {
    var text: String
    var selection: NSRange
    var byteSize: Int
    var changeCount: Int

    /// Swift's `String` equality would call "é" and "e" plus a combining accent the same; the pads don't.
    static func == (lhs: PadState, rhs: PadState) -> Bool {
        lhs.text.utf16.elementsEqual(rhs.text.utf16)
            && lhs.selection == rhs.selection
            && lhs.byteSize == rhs.byteSize
            && lhs.changeCount == rhs.changeCount
    }
}

/// The two pads, for the view to show and edit.
@MainActor
final class TwoPadsModel: ObservableObject {
    @Published private(set) var left: PadState
    @Published private(set) var right: PadState
    @Published private(set) var linked: Bool

    private let pads = TwoPads()

    init() {
        left = pads.left.state(selection: NSRange(location: 0, length: 0))
        right = pads.right.state(selection: NSRange(location: 0, length: 0))
        linked = pads.linked
    }

    deinit {
        pads.close()
    }

    /// The left pad as typed or selected in, with the right's selection kept on its characters.
    func editLeft(text: String, selection: NSRange) {
        let rightCaret = pads.right.caret(at: right.selection)
        if !text.utf16.elementsEqual(pads.left.text.utf16) { pads.typeLeft(text: text) }
        show(left: pads.left.state(selection: selection), right: pads.right.state(selection: pads.right.range(of: rightCaret)))
    }

    func editRight(text: String, selection: NSRange) {
        let leftCaret = pads.left.caret(at: left.selection)
        if !text.utf16.elementsEqual(pads.right.text.utf16) { pads.typeRight(text: text) }
        show(left: pads.left.state(selection: pads.left.range(of: leftCaret)), right: pads.right.state(selection: selection))
    }

    func setLinked(_ linked: Bool) {
        let leftCaret = pads.left.caret(at: left.selection)
        let rightCaret = pads.right.caret(at: right.selection)
        pads.linked = linked
        self.linked = linked
        show(
            left: pads.left.state(selection: pads.left.range(of: leftCaret)),
            right: pads.right.state(selection: pads.right.range(of: rightCaret))
        )
    }

    /// Publishes only a pad that changed, so a caret moved in one pad doesn't redraw the other.
    private func show(left: PadState, right: PadState) {
        if left != self.left { self.left = left }
        if right != self.right { self.right = right }
    }
}

/// A selection held as cursors, so each end stays on its character while the text changes.
private struct Caret {
    let start: KotlinByteArray
    let end: KotlinByteArray
}

private extension Pad {
    func caret(at selection: NSRange) -> Caret {
        Caret(start: cursorAt(index: Int32(selection.location)), end: cursorAt(index: Int32(NSMaxRange(selection))))
    }

    func range(of caret: Caret) -> NSRange {
        let start = Int(indexOf(cursor: caret.start))
        let end = Int(indexOf(cursor: caret.end))
        return NSRange(location: min(start, end), length: abs(end - start))
    }

    func state(selection: NSRange) -> PadState {
        PadState(text: text, selection: selection, byteSize: Int(byteSize), changeCount: Int(changeCount))
    }
}
