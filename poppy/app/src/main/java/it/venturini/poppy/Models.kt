package it.venturini.poppy

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Entry(
    val id: String,
    val type: String,          // seno | biberon | tiralatte | sonno
    val t: Long,
    val side: String?,         // sx | dx | both
    val dur: Int,              // secondi (seno)
    val ml: Int,
    val milk: String?,         // materno | formula
    val end: Long,             // fine sonno
    val note: String?,
    val session: String?,
    val kind: String? = null,   // pannolino: pipi|cacca|entrambi; solido: nuovo
    val name: String? = null,   // farmaco, rumore, solido, tappa
    val dose: String? = null,
    val wG: Int = 0,            // crescita: peso in grammi
    val lMm: Int = 0,           // lunghezza in mm
    val hMm: Int = 0,           // circonferenza cranica in mm
    val temp: Double = 0.0      // febbre, gradi C
)

data class TimerState(val side: String, val start: Long?, val session: String, val pausedAt: Long?)

data class Baby(
    val id: String,
    val name: String,
    val members: List<String>,
    val inviteCode: String,
    val timer: TimerState?,
    val sleepStart: Long?,
    val birth: Long? = null
)

private fun num(v: Any?): Long = (v as? Number)?.toLong() ?: 0L

fun parseEntry(id: String, m: Map<String, Any?>): Entry = Entry(
    id = id,
    type = m["type"] as? String ?: "",
    t = num(m["t"]),
    side = m["side"] as? String,
    dur = num(m["dur"]).toInt(),
    ml = num(m["ml"]).toInt(),
    milk = m["milk"] as? String,
    end = num(m["end"]),
    note = m["note"] as? String,
    session = m["session"] as? String,
    kind = m["kind"] as? String,
    name = m["name"] as? String,
    dose = m["dose"] as? String,
    wG = num(m["wG"]).toInt(),
    lMm = num(m["lMm"]).toInt(),
    hMm = num(m["hMm"]).toInt(),
    temp = (m["temp"] as? Number)?.toDouble() ?: 0.0
)

fun parseBaby(id: String, m: Map<String, Any?>): Baby {
    val tm = m["timer"] as? Map<*, *>
    val timer = if (tm != null) {
        TimerState(
            side = tm["side"] as? String ?: "sx",
            start = (tm["start"] as? Number)?.toLong(),
            session = tm["session"] as? String ?: "",
            pausedAt = (tm["pausedAt"] as? Number)?.toLong()
        )
    } else null
    val sl = m["sleep"] as? Map<*, *>
    return Baby(
        id = id,
        name = m["name"] as? String ?: "",
        members = (m["members"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        inviteCode = m["inviteCode"] as? String ?: "",
        timer = timer,
        sleepStart = (sl?.get("start") as? Number)?.toLong(),
        birth = (m["birth"] as? Number)?.toLong()
    )
}

// ---------- utilità ----------
fun nowMs(): Long = System.currentTimeMillis()

fun hDur(ms: Long): String {
    val m = (ms / 60000).coerceAtLeast(0L)
    val h = m / 60
    val mm = m % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d h %02d", h, mm)
    else String.format(Locale.getDefault(), "%d min", m)
}

fun clock(sec: Long): String {
    val s = sec.coerceAtLeast(0L)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, r)
    else String.format(Locale.getDefault(), "%02d:%02d", m, r)
}

fun startOfDay(t: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = t
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

/** null = impostazione di sistema, true = 24 h, false = 12 h */
var use24h: Boolean? = null

fun timeStr(t: Long): String = when (use24h) {
    true -> java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(t))
    false -> java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(t))
    else -> DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(t))
}
fun dateTimeStr(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t))
fun dayStr(t: Long): String = DateFormat.getDateInstance(DateFormat.FULL).format(Date(t))

private const val OZ = 29.5735

fun mlToUnit(ml: Int, oz: Boolean): Double = if (oz) ml / OZ else ml.toDouble()
fun unitToMl(v: Double, oz: Boolean): Int = if (oz) Math.round(v * OZ).toInt() else Math.round(v).toInt()
fun unitLabel(oz: Boolean): String = if (oz) "oz" else "ml"
fun fmtVol(ml: Int, oz: Boolean): String =
    if (oz) String.format(Locale.getDefault(), "%.1f oz", ml / OZ) else "$ml ml"
fun fmtNum(d: Double): String =
    if (d == Math.floor(d)) d.toLong().toString() else String.format(Locale.US, "%.1f", d)

// ---------- dati derivati per la home ----------
data class Session(val id: String, val segs: List<Entry>) {
    val t: Long get() = segs.minOf { it.t }
    val sec: Int get() = segs.sumOf { it.dur }
    val firstSide: String get() = segs.first().side ?: "sx"
}

data class Item(val key: String, val kind: String, val t: Long, val entry: Entry?, val session: Session?)

data class Derived(
    val items: List<Item>,
    val gap: Map<String, Long>,
    val lastFeed: Long?,
    val lastBottle: Long?,
    val lastPump: Long?,
    val lastDiaper: Long?,
    val lastBath: Long?,
    val nextSide: String
)

fun derive(entries: List<Entry>): Derived {
    val sessions = entries.filter { it.type == "seno" }
        .groupBy { it.session ?: it.id }
        .map { (k, v) -> Session(k, v.sortedBy { it.t }) }
    val items = mutableListOf<Item>()
    for (s in sessions) items.add(Item("s" + s.id, "seno", s.t, null, s))
    for (e in entries) if (e.type != "seno" && e.type != "tappa" && e.type != "crescita") {
        items.add(Item(e.id, e.type, e.t, e, null))
    }
    items.sortByDescending { it.t }
    val gap = HashMap<String, Long>()
    for (ty in listOf("biberon", "tiralatte")) {
        val l = items.filter { it.kind == ty }.sortedBy { it.t }
        for (i in 1 until l.size) gap[l[i].key] = l[i].t - l[i - 1].t
    }
    val lastBottle = items.firstOrNull { it.kind == "biberon" }?.t
    val lastPump = items.firstOrNull { it.kind == "tiralatte" }?.t
    val lastDiaper = items.firstOrNull { it.kind == "pannolino" }?.t
    val lastBath = items.firstOrNull { it.kind == "bagno" }?.t
    val lastFeed = items.firstOrNull { it.kind == "seno" || it.kind == "biberon" }?.t
    val lastS = sessions.maxByOrNull { it.t }
    val next = if (lastS?.firstSide == "sx") "dx" else "sx"
    return Derived(items, gap, lastFeed, lastBottle, lastPump, lastDiaper, lastBath, next)
}


// ---------- età e tappe ----------
const val VITD = "@vitd"

/** [mesi, settimane, giorni] dall'età compiuta. */
fun ageParts(birth: Long, now: Long): IntArray {
    val b = Calendar.getInstance().apply { timeInMillis = birth }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    var months = (n.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 12 + n.get(Calendar.MONTH) - b.get(Calendar.MONTH)
    if (n.get(Calendar.DAY_OF_MONTH) < b.get(Calendar.DAY_OF_MONTH)) months--
    if (months < 0) return intArrayOf(0, 0, 0)
    val anchor = Calendar.getInstance().apply { timeInMillis = birth; add(Calendar.MONTH, months) }
    val days = ((startOfDay(now) - startOfDay(anchor.timeInMillis)) / 86_400_000L).toInt().coerceAtLeast(0)
    return intArrayOf(months, days / 7, days % 7)
}

fun ageMonths(birth: Long?, now: Long): Int = if (birth == null) -1 else ageParts(birth, now)[0]

fun daysOld(birth: Long, now: Long): Int = ((startOfDay(now) - startOfDay(birth)) / 86_400_000L).toInt().coerceAtLeast(0)

/** Il DatePicker lavora in UTC: converto da/verso mezzanotte locale. */
fun localToPicker(t: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
    u.clear()
    u.set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    return u.timeInMillis
}

fun pickerToLocal(ms: Long): Long {
    val u = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = ms }
    val c = Calendar.getInstance()
    c.clear()
    c.set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
    return c.timeInMillis
}

data class Milestone(val key: String, val from: Int)

/** Età (mesi) in cui la tappa compare nell'elenco (un mese prima della tipica comparsa). */
val MILESTONES = listOf(
    Milestone("smile", 1), Milestone("follow", 1), Milestone("head", 2), Milestone("roll", 3),
    Milestone("sit", 5), Milestone("tooth", 5), Milestone("crawl", 7), Milestone("clap", 7),
    Milestone("stand", 8), Milestone("wave", 8), Milestone("cruise", 9), Milestone("step", 10),
    Milestone("word", 10)
)

const val SOLIDS_FROM_MONTHS = 5
