package kr.pleosapp.getoff.core.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kr.pleosapp.getoff.core.model.*
import kr.pleosapp.getoff.core.signals.VehicleSignals

enum class ItemKind { WINDOW, DOOR, WARNING, REAR }
enum class ItemStatus { Issue, Unverifiable }

data class ChecklistItem(
    val id: String,
    val kind: ItemKind,
    val text: String,
    val status: ItemStatus,
)

data class InspectionReport(val items: List<ChecklistItem>, val timedOut: Boolean) {
    val allClear get() = items.isEmpty()
}

data class InspectionOptions(
    val windows: Boolean = true,
    val doors: Boolean = true,
    val rear: Boolean = true,
    val warnings: Boolean = true,
)

class ExitInspector(private val signals: VehicleSignals, private val timeoutMs: Long = 3_000) {

    /** 신호를 병렬로 모으되 [timeoutMs] 안에 못 읽은 것은 Unknown으로 두고 부분 결과를 낸다(AC2.4). */
    suspend fun collect(opts: InspectionOptions = InspectionOptions()): VehicleSnapshot = coroutineScope {
        // 값을 내보내지 않고 끝나는 신호(미지원 등)는 기다리지 않고 바로 Unknown으로 둔다.
        suspend fun <T> read(f: Flow<T>): T? =
            withTimeoutOrNull(timeoutMs) { try { f.first() } catch (e: NoSuchElementException) { null } }
        val w = async { if (opts.windows) read(signals.windows) else emptyMap() }
        val d = async { if (opts.doors) read(signals.doors) else emptyMap() }
        val r = async { if (opts.rear) read(signals.rearOccupancy) else Occupancy.Empty }
        val s = async { if (opts.warnings) read(signals.warnings) else emptyList() }
        val wv = w.await()
        val dv = d.await()
        val rv = r.await()
        val sv = s.await()
        VehicleSnapshot(
            windows = wv ?: Seat.entries.associateWith { OpenState.Unknown },
            doors = dv ?: DoorId.entries.associateWith { OpenState.Unknown },
            rearOccupancy = rv ?: Occupancy.Unknown,
            warnings = sv,
            timedOut = listOf(wv, dv, rv, sv).any { it == null },
        )
    }

    suspend fun inspect(opts: InspectionOptions = InspectionOptions()): InspectionReport =
        evaluate(collect(opts), opts)

    companion object {
        /** 순수 함수: 스냅샷 → 이상 항목. 정상 항목은 만들지 않고, Unknown은 정상으로 보지 않는다(AC2.3). */
        fun evaluate(s: VehicleSnapshot, opts: InspectionOptions = InspectionOptions()): InspectionReport {
            val items = mutableListOf<ChecklistItem>()
            if (opts.windows) Seat.entries.forEach { seat ->
                when (s.windows[seat] ?: OpenState.Unknown) {
                    OpenState.Open -> items += ChecklistItem("window:$seat", ItemKind.WINDOW, Phrases.windowOpen(seat), ItemStatus.Issue)
                    OpenState.Unknown -> items += ChecklistItem("window:$seat", ItemKind.WINDOW, Phrases.windowUnknown(seat), ItemStatus.Unverifiable)
                    OpenState.Closed -> Unit
                }
            }
            if (opts.doors) DoorId.entries.forEach { door ->
                when (s.doors[door] ?: OpenState.Unknown) {
                    OpenState.Open -> items += ChecklistItem("door:$door", ItemKind.DOOR, Phrases.doorOpen(door), ItemStatus.Issue)
                    OpenState.Unknown -> items += ChecklistItem("door:$door", ItemKind.DOOR, Phrases.doorUnknown(door), ItemStatus.Unverifiable)
                    OpenState.Closed -> Unit
                }
            }
            if (opts.warnings) {
                val ws = s.warnings
                if (ws == null) items += ChecklistItem("warning:unknown", ItemKind.WARNING, Phrases.WARNINGS_UNKNOWN, ItemStatus.Unverifiable)
                else ws.forEach { items += ChecklistItem("warning:${it.code}", ItemKind.WARNING, Phrases.warning(it), ItemStatus.Issue) }
            }
            if (opts.rear) when (s.rearOccupancy) {
                Occupancy.Occupied -> items += ChecklistItem("rear", ItemKind.REAR, Phrases.REAR_OCCUPIED, ItemStatus.Issue)
                Occupancy.Unknown -> items += ChecklistItem("rear", ItemKind.REAR, Phrases.REAR_GENERIC, ItemStatus.Unverifiable) // AC3.1
                Occupancy.Empty -> Unit
            }
            return InspectionReport(items, s.timedOut)
        }

        /** 음성용 요약. 이상 없음이면 한 문장(AC2.1). */
        fun summary(r: InspectionReport): String =
            if (r.allClear) Phrases.ALL_CLEAR else r.items.joinToString(". ") { it.text }
    }
}
