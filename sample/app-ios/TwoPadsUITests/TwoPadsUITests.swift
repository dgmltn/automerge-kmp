import XCTest

/// The shared screen in the iOS app: what is typed goes through the binding, and the other pad shows it.
final class TwoPadsUITests: XCTestCase {
    private var app: XCUIApplication!

    override func setUp() {
        continueAfterFailure = false
        app = XCUIApplication()
        app.launch()
        XCTAssertTrue(app.textViews["Left"].waitForExistence(timeout: 60), "the app opens on the two pads")
        XCTAssertTrue(app.textViews["Right"].exists, "both pads are shown")
    }

    /// Compose serves each text field as a text view labelled with its label, but a filled field's
    /// label sits on its top edge and is gone once the keyboard scrolls it off. The pads are stacked
    /// on a phone, left above right, so their order finds them either way.
    private var left: XCUIElement { app.textViews.element(boundBy: 0) }
    private var right: XCUIElement { app.textViews.element(boundBy: 1) }

    private var linked: XCUIElement { app.switches["Linked"] }

    /// Typing at the end of the text: a tap below its last line puts the caret there.
    private func type(_ text: String, into pad: XCUIElement) {
        pad.tap()
        pad.typeText(text)
    }

    private func expect(_ pad: XCUIElement, toShow text: String, file: StaticString = #filePath, line: UInt = #line) {
        let found = XCTNSPredicateExpectation(predicate: NSPredicate(format: "value == %@", text), object: pad)
        XCTAssertEqual(
            XCTWaiter.wait(for: [found], timeout: 10), .completed,
            "shows \"\(text)\", not \"\(pad.value ?? "nil")\"", file: file, line: line
        )
    }

    func testTypingInTheLeft_showsInTheRight() {
        type("hello", into: left)

        expect(left, toShow: "hello")
        expect(right, toShow: "hello")
    }

    func testPadsEditedApart_showTheMergedTextOnceLinked() {
        type("one two three", into: left)
        expect(right, toShow: "one two three")

        linked.tap()
        XCTAssertFalse(linked.isSelected, "the pads are unlinked")

        type(" four", into: right)
        type(String(repeating: XCUIKeyboardKey.delete.rawValue, count: "three".count) + "3", into: left)
        expect(left, toShow: "one two 3")
        expect(right, toShow: "one two three four")

        linked.tap()
        XCTAssertTrue(linked.isSelected, "the pads are linked again")

        expect(left, toShow: "one two 3 four")
        expect(right, toShow: "one two 3 four")
    }
}
