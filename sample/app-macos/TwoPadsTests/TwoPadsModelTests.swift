import Combine
import Foundation
import XCTest
@testable import TwoPads

@MainActor
final class TwoPadsModelTests: XCTestCase {
    func testTypingInTheLeftPadWhileLinked_showsInTheRight() {
        let model = TwoPadsModel()

        model.editLeft(text: "hello", selection: NSRange(location: 5, length: 0))

        XCTAssertEqual(model.right.text, "hello")
        XCTAssertEqual(model.left.text, "hello")
    }

    func testPadsEditedApart_mergeToOneTextWhenLinkedAgain() {
        let model = TwoPadsModel()
        model.editLeft(text: "one two three", selection: NSRange(location: 13, length: 0))

        model.setLinked(false)
        model.editLeft(text: "one TWO three", selection: NSRange(location: 7, length: 0))
        model.editRight(text: "one two three four", selection: NSRange(location: 18, length: 0))
        XCTAssertEqual(model.left.text, "one TWO three")
        XCTAssertEqual(model.right.text, "one two three four")
        model.setLinked(true)

        XCTAssertTrue(model.linked)
        XCTAssertEqual(model.left.text, "one TWO three four")
        XCTAssertEqual(model.right.text, "one TWO three four")
        XCTAssertEqual(model.left.selection, NSRange(location: 7, length: 0))
        XCTAssertEqual(model.right.selection, NSRange(location: 18, length: 0))
    }

    func testACaret_keepsItsCharacter_whenTheOtherPadInsertsBeforeIt() {
        let model = TwoPadsModel()
        model.editLeft(text: "world", selection: NSRange(location: 5, length: 0))
        model.editRight(text: "world", selection: NSRange(location: 2, length: 0))

        model.editLeft(text: "hello world", selection: NSRange(location: 6, length: 0))

        XCTAssertEqual(model.right.selection, NSRange(location: 8, length: 0))
        XCTAssertEqual(model.left.selection, NSRange(location: 6, length: 0))
    }

    /// An emoji is two UTF-16 units, as NSRange counts, though Swift counts it as one Character.
    func testACaretAfterAnEmoji_keepsItsCharacter_whenTheOtherPadInsertsBeforeIt() {
        let model = TwoPadsModel()
        model.editLeft(text: "😀b", selection: NSRange(location: 3, length: 0))
        model.editRight(text: "😀b", selection: NSRange(location: 2, length: 0))

        model.editLeft(text: "XX😀b", selection: NSRange(location: 2, length: 0))

        XCTAssertEqual(model.right.text, "XX😀b")
        XCTAssertEqual(model.right.selection, NSRange(location: 4, length: 0))
    }

    func testASelection_keepsBothEnds_whenTheOtherPadInsertsBeforeIt() {
        let model = TwoPadsModel()
        model.editLeft(text: "one two", selection: NSRange(location: 7, length: 0))
        model.editRight(text: "one two", selection: NSRange(location: 4, length: 3))

        model.editLeft(text: "zero one two", selection: NSRange(location: 5, length: 0))

        XCTAssertEqual(model.right.selection, NSRange(location: 9, length: 3))
    }

    func testAPad_showsItsSizeAndChanges() {
        let model = TwoPadsModel()
        let start = model.left

        model.editLeft(text: "hello, world", selection: NSRange(location: 12, length: 0))

        XCTAssertGreaterThan(model.left.byteSize, start.byteSize)
        XCTAssertEqual(model.left.changeCount, start.changeCount + 1)
        XCTAssertEqual(model.right.changeCount, start.changeCount + 1)
    }

    func testACaretAtTheEnd_staysAtTheEnd_whenTheOtherPadInsertsBeforeIt() {
        let model = TwoPadsModel()
        model.editLeft(text: "abc", selection: NSRange(location: 3, length: 0))
        model.editRight(text: "abc", selection: NSRange(location: 3, length: 0))

        model.editLeft(text: "XXabc", selection: NSRange(location: 2, length: 0))

        XCTAssertEqual(model.right.selection, NSRange(location: 5, length: 0))
    }

    func testACaretAtTheEndAfterAnEmoji_staysAtTheEnd_whenTheOtherPadInsertsBeforeIt() {
        let model = TwoPadsModel()
        model.editLeft(text: "a😀", selection: NSRange(location: 3, length: 0))
        model.editRight(text: "a😀", selection: NSRange(location: 3, length: 0))

        model.editLeft(text: "XXa😀", selection: NSRange(location: 2, length: 0))

        XCTAssertEqual(model.right.selection, NSRange(location: 5, length: 0))
    }

    func testACaret_movesToWhatFollowed_whenTheOtherPadDeletesItsCharacter() {
        let model = TwoPadsModel()
        model.editLeft(text: "abc", selection: NSRange(location: 3, length: 0))
        model.editRight(text: "abc", selection: NSRange(location: 1, length: 0))

        model.editLeft(text: "ac", selection: NSRange(location: 1, length: 0))

        XCTAssertEqual(model.right.text, "ac")
        XCTAssertEqual(model.right.selection, NSRange(location: 1, length: 0))
    }

    func testACaretAtTheEnd_movesBack_whenTheOtherPadDeletesAnEmojiThere() {
        let model = TwoPadsModel()
        model.editLeft(text: "a😀", selection: NSRange(location: 3, length: 0))
        model.editRight(text: "a😀", selection: NSRange(location: 3, length: 0))

        model.editLeft(text: "a", selection: NSRange(location: 1, length: 0))

        XCTAssertEqual(model.right.selection, NSRange(location: 1, length: 0))
    }

    /// "é" as one precomposed unit and as "e" plus a combining accent: Swift calls them equal, Automerge does not.
    func testRetypingACanonicallyEquivalentText_reachesTheOtherPad() {
        let model = TwoPadsModel()
        model.editLeft(text: "\u{E9}", selection: NSRange(location: 1, length: 0))

        model.editLeft(text: "e\u{301}", selection: NSRange(location: 2, length: 0))

        XCTAssertEqual(Array(model.right.text.utf16), Array("e\u{301}".utf16))
    }

    func testMovingTheCaret_leavesTheOtherPadUnpublished() {
        let model = TwoPadsModel()
        model.editLeft(text: "abc", selection: NSRange(location: 3, length: 0))
        var published = 0
        let watch = model.$right.dropFirst().sink { _ in published += 1 }

        model.editLeft(text: "abc", selection: NSRange(location: 1, length: 0))

        XCTAssertEqual(published, 0)
        watch.cancel()
    }
}
