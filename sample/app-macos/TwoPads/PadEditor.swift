import AppKit
import SwiftUI

/// A plain-text `NSTextView`. SwiftUI's `TextEditor` can't be given a selection before macOS 15, and
/// each pad's caret is moved to follow its character when the other pad's edits arrive.
struct PadEditor: NSViewRepresentable {
    let text: String
    let selection: NSRange
    let onEdit: (String, NSRange) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onEdit: onEdit)
    }

    func makeNSView(context: Context) -> NSScrollView {
        context.coordinator.makeTextView()
    }

    func updateNSView(_ scrollView: NSScrollView, context: Context) {
        context.coordinator.onEdit = onEdit
        context.coordinator.show(text: text, selection: selection, in: scrollView.documentView as! NSTextView)
    }

    /// Writes the pad into the text view and reports what is typed there, but never its own writes.
    @MainActor
    final class Coordinator: NSObject, NSTextViewDelegate {
        var onEdit: (String, NSRange) -> Void
        private var showing = false

        init(onEdit: @escaping (String, NSRange) -> Void) {
            self.onEdit = onEdit
        }

        func makeTextView() -> NSScrollView {
            let scrollView = NSTextView.scrollableTextView()
            let textView = scrollView.documentView as! NSTextView
            textView.isRichText = false
            textView.font = .systemFont(ofSize: NSFont.systemFontSize)
            textView.textContainerInset = NSSize(width: 4, height: 4)
            // A substitution rewrites what was typed, which would muddy what each pad shows of a merge.
            textView.isAutomaticQuoteSubstitutionEnabled = false
            textView.isAutomaticDashSubstitutionEnabled = false
            textView.isAutomaticTextReplacementEnabled = false
            textView.isAutomaticSpellingCorrectionEnabled = false
            textView.isAutomaticLinkDetectionEnabled = false
            textView.isAutomaticDataDetectionEnabled = false
            textView.delegate = self
            return scrollView
        }

        func show(text: String, selection: NSRange, in textView: NSTextView) {
            // Deliberately left alone mid-composition (an accent, or an input method): the view's text
            // is not yet the pad's, and replacing it would cancel what is being composed.
            guard !textView.hasMarkedText() else { return }
            showing = true
            defer { showing = false }
            if !textView.string.utf16.elementsEqual(text.utf16) { textView.string = text }
            if textView.selectedRange() != selection { textView.setSelectedRange(selection) }
        }

        func textDidChange(_ notification: Notification) {
            report(notification)
        }

        func textViewDidChangeSelection(_ notification: Notification) {
            report(notification)
        }

        private func report(_ notification: Notification) {
            guard !showing, let textView = notification.object as? NSTextView, !textView.hasMarkedText() else { return }
            onEdit(textView.string, textView.selectedRange())
        }
    }
}
