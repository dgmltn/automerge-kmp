import SwiftUI

/// Two devices' copies of one text, side by side, and the switch that links them.
struct TwoPadsView: View {
    @ObservedObject var model: TwoPadsModel

    var body: some View {
        HStack(spacing: 16) {
            PadPane(label: "Left", state: model.left, onEdit: model.editLeft)
            Toggle(
                "Linked",
                isOn: Binding(get: { model.linked }, set: { model.setLinked($0) })
            )
            .toggleStyle(.switch)
            PadPane(label: "Right", state: model.right, onEdit: model.editRight)
        }
        .padding(16)
        .frame(minWidth: 600, minHeight: 240)
    }
}

/// One pad's text area, with how big its document is beneath it.
struct PadPane: View {
    let label: String
    let state: PadState
    let onEdit: (String, NSRange) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.headline)
            PadEditor(text: state.text, selection: state.selection, onEdit: onEdit)
                .border(Color(nsColor: .separatorColor))
            Text("\(state.byteSize) bytes · \(changes)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var changes: String {
        state.changeCount == 1 ? "1 change" : "\(state.changeCount) changes"
    }
}
