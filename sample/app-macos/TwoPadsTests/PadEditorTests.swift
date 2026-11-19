import AppKit
import XCTest
@testable import TwoPads

@MainActor
final class PadEditorTests: XCTestCase {
    private var edits: [(String, NSRange)] = []
    private lazy var coordinator = PadEditor.Coordinator { [unowned self] in edits.append(($0, $1)) }
    private lazy var textView = coordinator.makeTextView().documentView as! NSTextView

    func testShowingThePad_isNotReportedAsAnEdit() {
        coordinator.show(text: "😀b", selection: NSRange(location: 2, length: 0), in: textView)

        XCTAssertTrue(edits.isEmpty)
        XCTAssertEqual(textView.string, "😀b")
        XCTAssertEqual(textView.selectedRange(), NSRange(location: 2, length: 0))
    }

    func testMovingTheCaretToFollowAMerge_isNotReportedAsAnEdit() {
        coordinator.show(text: "😀b", selection: NSRange(location: 2, length: 0), in: textView)

        coordinator.show(text: "XX😀b", selection: NSRange(location: 4, length: 0), in: textView)

        XCTAssertTrue(edits.isEmpty)
        XCTAssertEqual(textView.selectedRange(), NSRange(location: 4, length: 0))
    }

    func testTyping_isReportedWithTheCaretInUtf16Units() {
        coordinator.show(text: "😀", selection: NSRange(location: 2, length: 0), in: textView)

        textView.insertText("b", replacementRange: textView.selectedRange())

        XCTAssertEqual(edits.last?.0, "😀b")
        XCTAssertEqual(edits.last?.1, NSRange(location: 3, length: 0))
    }

    /// Substitution happens only on real keystrokes, so the settings are what can be checked here.
    func testWhatIsTyped_isNotRewritten() {
        XCTAssertFalse(textView.isAutomaticQuoteSubstitutionEnabled)
        XCTAssertFalse(textView.isAutomaticDashSubstitutionEnabled)
        XCTAssertFalse(textView.isAutomaticTextReplacementEnabled)
        XCTAssertFalse(textView.isAutomaticSpellingCorrectionEnabled)
    }

    func testACanonicallyEquivalentText_isShownAsThePadHasIt() {
        coordinator.show(text: "\u{E9}", selection: NSRange(location: 1, length: 0), in: textView)

        coordinator.show(text: "e\u{301}", selection: NSRange(location: 2, length: 0), in: textView)

        XCTAssertEqual(Array(textView.string.utf16), Array("e\u{301}".utf16))
    }
}
