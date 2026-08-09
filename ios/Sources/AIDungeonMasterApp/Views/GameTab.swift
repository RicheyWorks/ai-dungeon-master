import SwiftUI
import AIDungeonMasterClient

struct GameTab: View {
    @ObservedObject var model: GameViewModel
    @State private var prompt = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                sessionActions
                if let status = model.status {
                    questCard(status)
                    partySection(status)
                    chronicleSection(status)
                    choicesSection(status)
                }
                Divider()
                narrationSection
            }
            .padding()
        }
    }

    private var sessionActions: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button("Save") { model.saveGame() }
                    .disabled(model.busy)
                Button(model.saveExists == false ? "Load · none" : "Load") { model.loadGame() }
                    .disabled(model.busy || model.saveExists == false)
                Button("Reset") { model.resetGame() }
                    .disabled(model.busy)
            }
            .buttonStyle(.bordered)
            HStack {
                Text(saveMetaLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                Spacer()
                Button("Clear save") { model.deleteSave() }
                    .disabled(model.busy || model.saveExists != true)
                    .buttonStyle(.bordered)
            }
        }
        .task {
            await model.refreshSaveMeta()
        }
    }

    private var saveMetaLabel: String {
        if model.saveExists == true {
            if let b = model.saveBytes {
                return "Save ready · \(b) bytes"
            }
            return "Save ready"
        }
        if model.saveExists == false {
            return "No save for this session"
        }
        return "Save status unknown"
    }

    private func questCard(_ status: GameStatusV2) -> some View {
        let quest = status.quest
        let outcome: String = {
            if quest?.completed == true { return "Completed" }
            if quest?.failed == true { return "Failed" }
            if status.combatActive == true { return "In combat!" }
            return "In progress"
        }()
        return VStack(alignment: .leading, spacing: 8) {
            Text(quest?.title ?? "No active quest")
                .font(.title2.bold())
            Text("\(outcome) · Chaos \(status.chaosLevel.map(String.init) ?? "?")")
                .foregroundStyle(.secondary)
            ProgressView(value: min(max(quest?.progress ?? 0, 0), 1))
            if let location = status.location {
                Text("Location: \(location)")
                    .font(.caption)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func partySection(_ status: GameStatusV2) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Party").font(.headline)
            ForEach(Array((status.party ?? []).enumerated()), id: \.offset) { _, member in
                memberCard(member)
            }
        }
    }

    private func memberCard(_ member: MemberState) -> some View {
        let hp = max(member.hp ?? 0, 0)
        let maxHp = max(member.maxHp ?? 1, 1)
        return VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(member.name ?? "?")
                    .font(.headline)
                Spacer()
                Text("\(member.role ?? "") L\(member.level ?? 1)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            ProgressView(value: Double(hp) / Double(maxHp))
            Text(memberLine(member, hp: hp, maxHp: maxHp))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding()
        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
    }

    private func memberLine(_ member: MemberState, hp: Int, maxHp: Int) -> String {
        var s = "HP \(hp)/\(maxHp)"
        if let mana = member.mana {
            s += " · MP \(mana)/\(member.maxMana ?? mana)"
        }
        if member.alive == false { s += " · FALLEN" }
        let statuses = member.statuses ?? []
        if !statuses.isEmpty { s += " · \(statuses.joined(separator: ", "))" }
        return s
    }

    private func chronicleSection(_ status: GameStatusV2) -> some View {
        let events = status.recentEvents ?? []
        return Group {
            if !events.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Text("The story so far").font(.headline)
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(Array(events.enumerated()), id: \.offset) { _, line in
                            Text(line).font(.caption)
                        }
                    }
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }

    private func choicesSection(_ status: GameStatusV2) -> some View {
        let choices = status.availableChoices ?? []
        return VStack(alignment: .leading, spacing: 8) {
            Text("Choices").font(.headline)
            if choices.isEmpty {
                Text("No choices available.")
                    .foregroundStyle(.secondary)
            }
            ForEach(choices, id: \.self) { label in
                Button(label) { model.act(choiceLabel: label) }
                    .buttonStyle(.borderedProminent)
                    .disabled(model.busy)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var narrationSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(model.stompConnected ? "Ask the Dungeon Master (live stream)" : "Ask the Dungeon Master")
                .font(.headline)
            TextField("What do you do?", text: $prompt, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .lineLimit(2...4)
            Button(model.stompConnected ? "Stream narrate" : "Narrate") {
                guard !prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
                model.narrate(prompt: prompt)
            }
            .buttonStyle(.borderedProminent)
            .disabled(model.busy || prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

            if !model.streamBuffer.isEmpty {
                Text(model.streamBuffer)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.tertiary.opacity(0.3), in: RoundedRectangle(cornerRadius: 12))
            }
            if let narration = model.narration {
                Text(narration)
                    .padding()
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))
            }
        }
    }
}
