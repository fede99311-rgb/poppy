package it.venturini.poppy

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File

/** Report PDF per il pediatra: riepilogo per giorno degli ultimi [days] giorni + crescita, farmaci, febbre. */
fun buildReport(ctx: Context, baby: Baby, entries: List<Entry>, health: List<Entry>, days: Int, oz: Boolean): File {
    val d = derive(entries)
    val doc = PdfDocument()
    val w = 595
    val h = 842
    val margin = 40f
    val body = Paint().apply { textSize = 10f; isAntiAlias = true }
    val bold = Paint(body).apply { typeface = Typeface.DEFAULT_BOLD }
    val big = Paint(body).apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
    var pageNo = 1
    var page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
    var c = page.canvas
    var y = margin

    fun newPage() {
        doc.finishPage(page)
        pageNo++
        page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
        c = page.canvas
        y = margin
    }
    fun line(txt: String, p: Paint = body, gap: Float = 15f) {
        if (y > h - margin) newPage()
        c.drawText(txt, margin, y, p)
        y += gap
    }

    line(ctx.getString(R.string.report_title, baby.name), big, 26f)
    val now = nowMs()
    val sub = StringBuilder(dateTimeStr(now))
    if (baby.birth != null) {
        val age = ageParts(baby.birth, now)
        sub.append(" · ").append(ctx.getString(R.string.report_age, age[0], age[1]))
    }
    line(sub.toString(), body, 22f)

    line(ctx.getString(R.string.report_daily, days), bold, 18f)
    val day = 86_400_000L
    val today = startOfDay(now)
    for (n in 0 until days) {
        val s = today - n * day
        val e = s + day
        val of = d.items.filter { it.t in s until e }
        val breast = of.count { it.kind == "seno" }
        val bottles = of.filter { it.kind == "biberon" }
        val pumps = of.filter { it.kind == "tiralatte" }
        val sleepMs = of.filter { it.kind == "sonno" }.sumOf { x ->
            val en = x.entry
            if (en != null && en.end > en.t) en.end - en.t else 0L
        }
        val pee = of.count { it.kind == "pannolino" && (it.entry?.kind == "pipi" || it.entry?.kind == "entrambi") }
        val poo = of.count { it.kind == "pannolino" && (it.entry?.kind == "cacca" || it.entry?.kind == "entrambi") }
        val txt = ctx.getString(
            R.string.report_row,
            java.text.DateFormat.getDateInstance(java.text.DateFormat.SHORT).format(java.util.Date(s)),
            breast, bottles.size, fmtVol(bottles.sumOf { it.entry?.ml ?: 0 }, oz),
            fmtVol(pumps.sumOf { it.entry?.ml ?: 0 }, oz), hDur(sleepMs), pee, poo
        )
        line(txt)
    }
    y += 8f

    val growth = health.filter { it.type == "crescita" }.sortedByDescending { it.t }
    if (growth.isNotEmpty()) {
        line(ctx.getString(R.string.type_growth), bold, 18f)
        for (g in growth.take(12)) {
            val parts = listOfNotNull(
                if (g.wG > 0) fmtNum(g.wG / 1000.0) + " kg" else null,
                if (g.lMm > 0) fmtNum(g.lMm / 10.0) + " cm" else null,
                if (g.hMm > 0) fmtNum(g.hMm / 10.0) + " cm (head)" else null
            )
            line(dateTimeStr(g.t) + "  " + parts.joinToString(" · "))
        }
        y += 8f
    }
    val meds = health.filter { it.type == "farmaco" }.sortedByDescending { it.t }
    if (meds.isNotEmpty()) {
        line(ctx.getString(R.string.type_med), bold, 18f)
        for (m in meds.take(40)) {
            val nm = if (m.name == VITD) ctx.getString(R.string.vitd) else (m.name ?: "")
            line(dateTimeStr(m.t) + "  " + nm + (if (!m.dose.isNullOrBlank()) " · " + m.dose else ""))
        }
        y += 8f
    }
    val fevers = health.filter { it.type == "febbre" }.sortedByDescending { it.t }
    if (fevers.isNotEmpty()) {
        line(ctx.getString(R.string.type_fever), bold, 18f)
        for (f in fevers.take(20)) line(dateTimeStr(f.t) + "  " + fmtNum(f.temp) + " °C")
    }
    doc.finishPage(page)
    val dir = File(ctx.cacheDir, "export")
    dir.mkdirs()
    val f = File(dir, "poppy-report.pdf")
    f.outputStream().use { doc.writeTo(it) }
    doc.close()
    return f
}
