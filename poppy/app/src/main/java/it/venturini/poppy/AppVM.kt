package it.venturini.poppy

import android.app.Activity
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class AppVM : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    var user by mutableStateOf<FirebaseUser?>(auth.currentUser)
    var babies by mutableStateOf<List<Baby>>(emptyList())
    var babiesLoaded by mutableStateOf(false)
    var selectedId by mutableStateOf<String?>(null)
    var entries by mutableStateOf<List<Entry>>(emptyList())
    var message by mutableStateOf<Int?>(null)
    var oz by mutableStateOf(false)
        private set
    var health by mutableStateOf<List<Entry>>(emptyList())
    var ideas by mutableStateOf<List<Idea>>(emptyList())
    private var ideasReg: ListenerRegistration? = null
    var themeMode by mutableStateOf(0)          // 0 sistema, 1 chiaro, 2 scuro
        private set
    var hourMode by mutableStateOf(0)           // 0 sistema, 1 24 h, 2 12 h
        private set
    var minDur by mutableStateOf(3)             // durata minima poppata (s)
        private set
    var fixedSide by mutableStateOf("auto")     // auto | sx | dx
        private set
    var lockEnabled by mutableStateOf(false)
        private set
    var locked by mutableStateOf(false)
    var lastUnlock = 0L
    var remFeedH by mutableStateOf(0)
        private set
    var remVitD by mutableStateOf(false)
        private set
    var vitdHour by mutableStateOf(9)
        private set
    var pendingStart by mutableStateOf<String?>(null)

    private var prefs: SharedPreferences? = null
    private var babiesReg: ListenerRegistration? = null
    private var entriesReg: ListenerRegistration? = null
    private var healthReg: ListenerRegistration? = null

    private val authListener = FirebaseAuth.AuthStateListener { a ->
        user = a.currentUser
        val u = a.currentUser
        if (u != null) listenBabies(u.uid) else stopAll()
    }

    init {
        auth.addAuthStateListener(authListener)
    }

    fun initPrefs(p: SharedPreferences) {
        prefs = p
        oz = p.getBoolean("oz", false)
        themeMode = p.getInt("theme", 0)
        hourMode = p.getInt("hours", 0)
        use24h = when (hourMode) { 1 -> true; 2 -> false; else -> null }
        minDur = p.getInt("min_dur", 3)
        fixedSide = p.getString("fixed_side", "auto") ?: "auto"
        lockEnabled = p.getBoolean("lock", false)
        remFeedH = p.getInt("rem_feed_h", 0)
        remVitD = p.getBoolean("rem_vitd", false)
        vitdHour = p.getInt("vitd_hour", 9)
        if (lockEnabled) locked = true
    }

    fun setTheme(v: Int) { themeMode = v; prefs?.edit()?.putInt("theme", v)?.apply() }
    fun setHours(v: Int) {
        hourMode = v
        use24h = when (v) { 1 -> true; 2 -> false; else -> null }
        prefs?.edit()?.putInt("hours", v)?.apply()
    }
    fun changeMinDur(v: Int) { minDur = v; prefs?.edit()?.putInt("min_dur", v)?.apply() }
    fun changeFixedSide(v: String) { fixedSide = v; prefs?.edit()?.putString("fixed_side", v)?.apply() }
    fun setLock(v: Boolean) { lockEnabled = v; prefs?.edit()?.putBoolean("lock", v)?.apply() }
    fun setRemFeed(h: Int) { remFeedH = h; prefs?.edit()?.putInt("rem_feed_h", h)?.apply() }
    fun setRemVitD(on: Boolean, hour: Int) {
        remVitD = on; vitdHour = hour
        prefs?.edit()?.putBoolean("rem_vitd", on)?.putInt("vitd_hour", hour)?.putInt("vitd_min", 0)?.apply()
    }
    fun seen(key: String): Boolean = prefs?.getBoolean("seen_$key", false) ?: true
    fun markSeen(key: String) { prefs?.edit()?.putBoolean("seen_$key", true)?.apply() }
    fun saveBabyPrefs(id: String, name: String, lastFeed: Long?, nextSide: String) {
        prefs?.edit()?.putString("babyId", id)?.putString("babyName", name)
            ?.putLong("lastFeed", lastFeed ?: 0L)?.putString("nextSide", nextSide)?.apply()
    }

    fun changeOz(v: Boolean) {
        oz = v
        prefs?.edit()?.putBoolean("oz", v)?.apply()
    }

    override fun onCleared() {
        auth.removeAuthStateListener(authListener)
        stopAll()
    }

    private fun stopAll() {
        babiesReg?.remove(); babiesReg = null
        entriesReg?.remove(); entriesReg = null
        healthReg?.remove(); healthReg = null
        ideasReg?.remove(); ideasReg = null; ideas = emptyList()
        babies = emptyList(); entries = emptyList(); health = emptyList(); selectedId = null; babiesLoaded = false
    }

    // ---------- accesso ----------
    suspend fun signIn(activity: Activity, webClientId: String) {
        val cm = CredentialManager.create(activity)
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val cred = cm.getCredential(activity, req).credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val g = GoogleIdTokenCredential.createFrom(cred.data)
            auth.signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await()
        } else {
            throw IllegalStateException("credenziale non valida")
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // ---------- ascolto dati ----------
    private fun listenBabies(uid: String) {
        babiesReg?.remove()
        babiesLoaded = false
        viewModelScope.launch {
            delay(5000)
            babiesLoaded = true
        }
        babiesReg = db.collection("babies").whereArrayContains("members", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    babiesLoaded = true
                    return@addSnapshotListener
                }
                val list = snap.documents.map { parseBaby(it.id, it.data ?: emptyMap()) }
                    .sortedBy { it.name.lowercase() }
                babies = list
                if (!(snap.metadata.isFromCache && list.isEmpty())) babiesLoaded = true
                if (selectedId == null || list.none { it.id == selectedId }) {
                    selectBaby(list.firstOrNull()?.id)
                }
            }
    }

    fun selectBaby(id: String?) {
        if (id == selectedId && entriesReg != null) return
        selectedId = id
        entriesReg?.remove(); entriesReg = null
        healthReg?.remove(); healthReg = null
        entries = emptyList()
        health = emptyList()
        if (id == null) return
        prefs?.edit()?.putString("babyId", id)?.apply()
        val since = nowMs() - 30L * 24 * 3600 * 1000
        entriesReg = col(id).whereGreaterThanOrEqualTo("t", since)
            .orderBy("t", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { s, err ->
                if (err != null) message = R.string.sync_error
                if (s != null) entries = s.documents.map { parseEntry(it.id, it.data ?: emptyMap()) }
            }
        healthReg = col(id).whereIn("type", listOf("crescita", "tappa", "farmaco", "febbre", "solido", "bagno"))
            .addSnapshotListener(MetadataChanges.INCLUDE) { s, err ->
                if (err != null) message = R.string.sync_error
                if (s != null) health = s.documents.map { parseEntry(it.id, it.data ?: emptyMap()) }.sortedByDescending { it.t }
            }
    }

    private fun col(babyId: String) = db.collection("babies").document(babyId).collection("entries")
    private fun bref(babyId: String) = db.collection("babies").document(babyId)
    private fun uid12() = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val r = SecureRandom()
        return (1..8).map { alphabet[r.nextInt(alphabet.length)] }.joinToString("")
    }

    // ---------- bambini e famiglia ----------
    fun createBaby(name: String, birth: Long? = null) {
        val u = auth.currentUser ?: return
        val ref = db.collection("babies").document()
        val code = newCode()
        val b = db.batch()
        b.set(
            ref,
            mapOf(
                "name" to name.trim(),
                "members" to listOf(u.uid),
                "inviteCode" to code,
                "createdAt" to nowMs(),
                "timer" to null,
                "sleep" to null,
                "birth" to birth
            )
        )
        b.set(db.collection("invites").document(code), mapOf("babyId" to ref.id, "createdBy" to u.uid))
        b.commit()
        selectBaby(ref.id)
    }

    fun renameBaby(babyId: String, name: String) {
        if (name.isBlank()) return
        bref(babyId).update("name", name.trim())
    }

    fun setBirth(babyId: String, birth: Long) {
        bref(babyId).update("birth", birth)
    }

    fun regenerateCode(baby: Baby) {
        val u = auth.currentUser ?: return
        val code = newCode()
        val b = db.batch()
        if (baby.inviteCode.isNotEmpty()) b.delete(db.collection("invites").document(baby.inviteCode))
        b.set(db.collection("invites").document(code), mapOf("babyId" to baby.id, "createdBy" to u.uid))
        b.update(bref(baby.id), "inviteCode", code)
        b.commit()
    }

    fun joinWithCode(code: String, onResult: (Boolean) -> Unit) {
        val u = auth.currentUser
        val c = code.trim().uppercase()
        if (u == null || c.isEmpty()) {
            onResult(false); return
        }
        db.collection("invites").document(c).get()
            .addOnSuccessListener { d ->
                val bid = d.getString("babyId")
                if (!d.exists() || bid == null) {
                    onResult(false)
                } else {
                    bref(bid).update(mapOf("members" to FieldValue.arrayUnion(u.uid), "joinCode" to c))
                        .addOnSuccessListener { selectBaby(bid); onResult(true) }
                        .addOnFailureListener { onResult(false) }
                }
            }
            .addOnFailureListener { onResult(false) }
    }

    private suspend fun purgeBaby(b: Baby) {
        val c = col(b.id)
        while (true) {
            val snap = c.limit(400).get().await()
            if (snap.isEmpty) break
            val batch = db.batch()
            for (d in snap.documents) batch.delete(d.reference)
            batch.commit().await()
        }
        if (b.inviteCode.isNotEmpty()) db.collection("invites").document(b.inviteCode).delete().await()
        bref(b.id).delete().await()
    }

    fun deleteBaby(b: Baby, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                purgeBaby(b)
            } catch (e: Exception) {
                message = R.string.error_generic
            }
            onDone()
        }
    }

    /** onResult: null = ok, "relogin" o "error". */
    fun deleteAccount(onResult: (String?) -> Unit) {
        val u = auth.currentUser ?: return
        viewModelScope.launch {
            try {
                for (b in babies) {
                    if (b.members.size <= 1) purgeBaby(b)
                    else bref(b.id).update("members", FieldValue.arrayRemove(u.uid)).await()
                }
                u.delete().await()
                onResult(null)
            } catch (e: FirebaseAuthRecentLoginRequiredException) {
                onResult("relogin")
            } catch (e: Exception) {
                onResult("error")
            }
        }
    }

    // ---------- registrazioni ----------
    private val healthTypes = setOf("crescita", "tappa", "farmaco", "febbre", "solido", "bagno")

    private fun putLocal(e: Entry) {
        entries = (entries.filter { it.id != e.id } + e).sortedByDescending { it.t }
        if (e.type in healthTypes) health = (health.filter { it.id != e.id } + e).sortedByDescending { it.t }
    }

    private fun dropLocal(id: String) {
        entries = entries.filter { it.id != id }
        health = health.filter { it.id != id }
    }

    fun saveEntry(babyId: String, existingId: String?, data: Map<String, Any?>, onDone: (String) -> Unit) {
        val c = col(babyId)
        val ref = if (existingId != null) c.document(existingId) else c.document()
        val clean = data.filterValues { it != null }
        try { putLocal(parseEntry(ref.id, clean)) } catch (e: Exception) { }
        ref.set(clean).addOnFailureListener {
            message = R.string.save_failed
            if (existingId == null) dropLocal(ref.id)
        }
        onDone(ref.id)
    }

    fun deleteEntry(babyId: String, id: String) {
        dropLocal(id)
        col(babyId).document(id).delete().addOnFailureListener { message = R.string.save_failed }
    }

    fun deleteSession(babyId: String, segs: List<Entry>) {
        val ids = segs.map { it.id }.toSet()
        entries = entries.filter { it.id !in ids }
        val b = db.batch()
        for (s in segs) b.delete(col(babyId).document(s.id))
        b.commit()
    }


    // ---------- idee e richieste ----------
    fun listenIdeas() {
        if (ideasReg != null) return
        ideasReg = db.collection("ideas").orderBy("votes", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, err ->
                if (err != null) message = R.string.sync_error
                if (s != null) ideas = s.documents.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    Idea(
                        d.id, d.getString("text") ?: "", (d.getLong("votes") ?: 0L).toInt(),
                        (d.get("voters") as? List<String>) ?: emptyList(),
                        d.getString("author") ?: "", d.getString("status") ?: "open"
                    )
                }
            }
    }

    fun submitIdea(text: String, onDone: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val t = text.trim().take(200)
        if (t.length < 5) return
        db.collection("ideas").document().set(
            mapOf("text" to t, "author" to uid, "votes" to 1, "voters" to listOf(uid),
                "status" to "open", "createdAt" to nowMs())
        ).addOnSuccessListener { message = R.string.idea_thanks; onDone() }
            .addOnFailureListener { message = R.string.save_failed }
    }

    fun voteIdea(i: Idea) {
        val uid = auth.currentUser?.uid ?: return
        if (uid in i.voters) return
        db.collection("ideas").document(i.id).update(
            mapOf("voters" to FieldValue.arrayUnion(uid), "votes" to FieldValue.increment(1))
        ).addOnFailureListener { message = R.string.save_failed }
    }

    fun deleteIdea(i: Idea) {
        db.collection("ideas").document(i.id).delete().addOnFailureListener { message = R.string.save_failed }
    }

    fun myUid(): String = auth.currentUser?.uid ?: ""

    // ---------- timer poppata (condiviso tra i telefoni) ----------
    private fun addSegment(b: WriteBatch, babyId: String, tm: TimerState, now: Long) {
        val start = tm.start ?: return
        val dur = Math.round((now - start) / 1000.0).toInt()
        if (dur >= minDur) {
            b.set(
                col(babyId).document(),
                mapOf("type" to "seno", "t" to start, "dur" to dur, "side" to tm.side, "session" to tm.session)
            )
        }
    }

    private fun addSleep(b: WriteBatch, babyId: String, start: Long, end: Long) {
        if (end - start >= 60000) {
            b.set(col(babyId).document(), mapOf("type" to "sonno", "t" to start, "end" to end))
        }
    }

    fun timer(baby: Baby, action: String, side: String? = null, session: String? = null) {
        val now = nowMs()
        val tm = baby.timer
        val running = tm?.start != null
        val b = db.batch()
        val ref = bref(baby.id)
        when (action) {
            "start" -> {
                if (tm != null && running) addSegment(b, baby.id, tm, now)
                val sl = baby.sleepStart
                if (sl != null) addSleep(b, baby.id, sl, now)
                b.update(
                    ref,
                    mapOf(
                        "timer" to mapOf("side" to (side ?: "sx"), "start" to now, "session" to uid12()),
                        "sleep" to null
                    )
                )
            }
            "switch" -> {
                if (tm == null || !running) return
                addSegment(b, baby.id, tm, now)
                val other = if (tm.side == "sx") "dx" else "sx"
                b.update(ref, mapOf("timer" to mapOf("side" to other, "start" to now, "session" to tm.session)))
            }
            "pause" -> {
                if (tm == null || !running) return
                addSegment(b, baby.id, tm, now)
                b.update(
                    ref,
                    mapOf("timer" to mapOf("side" to tm.side, "start" to null, "session" to tm.session, "pausedAt" to now))
                )
            }
            "resume" -> {
                val sess = session ?: tm?.session ?: return
                if (tm != null && running) addSegment(b, baby.id, tm, now)
                val last = entries.filter { it.session == sess }.maxByOrNull { it.t }
                val sd = side ?: tm?.side ?: last?.side ?: "sx"
                b.update(
                    ref,
                    mapOf("timer" to mapOf("side" to sd, "start" to now, "session" to sess), "sleep" to null)
                )
            }
            "stop" -> {
                if (tm != null && running) addSegment(b, baby.id, tm, now)
                b.update(ref, mapOf("timer" to null))
            }
            "cancel" -> b.update(ref, mapOf("timer" to null))
            else -> return
        }
        b.commit()
    }

    fun sleep(baby: Baby, action: String) {
        val now = nowMs()
        val b = db.batch()
        val ref = bref(baby.id)
        when (action) {
            "start" -> b.update(ref, mapOf("sleep" to mapOf("start" to now)))
            "stop" -> {
                val sl = baby.sleepStart
                if (sl != null) addSleep(b, baby.id, sl, now)
                b.update(ref, mapOf("sleep" to null))
            }
            "cancel" -> b.update(ref, mapOf("sleep" to null))
            else -> return
        }
        b.commit()
    }

    // ---------- esportazione ----------
    suspend fun exportCsv(cacheDir: File, baby: Baby): File {
        val snap = col(baby.id).orderBy("t", Query.Direction.ASCENDING).get().await()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val sb = StringBuilder("\uFEFFdate;type;side;duration_min;volume_ml;milk;sleep_end;kind;name;dose;weight_g;length_mm;head_mm;temp_c;note\n")
        for (d in snap.documents) {
            val e = parseEntry(d.id, d.data ?: emptyMap())
            sb.append(fmt.format(Date(e.t))).append(';')
                .append(e.type).append(';')
                .append(e.side ?: "").append(';')
                .append(if (e.dur > 0) String.format(Locale.US, "%.1f", e.dur / 60.0) else "").append(';')
                .append(if (e.ml > 0) e.ml.toString() else "").append(';')
                .append(e.milk ?: "").append(';')
                .append(if (e.type == "sonno" && e.end > 0) fmt.format(Date(e.end)) else "").append(';')
                .append(csvClean(e.kind)).append(';')
                .append(csvClean(e.name)).append(';')
                .append(csvClean(e.dose)).append(';')
                .append(if (e.wG > 0) e.wG.toString() else "").append(';')
                .append(if (e.lMm > 0) e.lMm.toString() else "").append(';')
                .append(if (e.hMm > 0) e.hMm.toString() else "").append(';')
                .append(if (e.temp > 0) String.format(Locale.US, "%.1f", e.temp) else "").append(';')
                .append(csvClean(e.note)).append('\n')
        }
        val dir = File(cacheDir, "export")
        dir.mkdirs()
        val safe = baby.name.replace(Regex("[^A-Za-z0-9]"), "_").ifEmpty { "baby" }
        val f = File(dir, "poppy-$safe.csv")
        f.writeText(sb.toString())
        return f
    }

    private fun csvClean(x: String?): String = (x ?: "").replace(";", ",").replace("\n", " ")

    // ---------- backup JSON ----------
    suspend fun exportJson(cacheDir: File, baby: Baby): File {
        val snap = col(baby.id).get().await()
        val arr = org.json.JSONArray()
        for (d in snap.documents) {
            val o = org.json.JSONObject()
            o.put("_id", d.id)
            for ((k, v) in d.data ?: emptyMap()) {
                if (v is Number || v is String || v is Boolean) o.put(k, v)
            }
            arr.put(o)
        }
        val root = org.json.JSONObject()
        root.put("app", "poppy")
        root.put("version", 1)
        root.put("baby", baby.name)
        root.put("entries", arr)
        val dir = File(cacheDir, "export")
        dir.mkdirs()
        val safe = baby.name.replace(Regex("[^A-Za-z0-9]"), "_").ifEmpty { "baby" }
        val f = File(dir, "poppy-backup-$safe.json")
        f.writeText(root.toString())
        return f
    }

    fun importJson(babyId: String, text: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val root = org.json.JSONObject(text)
                if (root.optString("app") != "poppy") {
                    onDone(-1)
                    return@launch
                }
                val arr = root.getJSONArray("entries")
                var n = 0
                var batch = db.batch()
                var inBatch = 0
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val id = o.optString("_id", "")
                    val map = HashMap<String, Any?>()
                    val keys = o.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        if (k != "_id") map[k] = o.get(k)
                    }
                    val ref = if (id.isEmpty()) col(babyId).document() else col(babyId).document(id)
                    batch.set(ref, map)
                    n++
                    inBatch++
                    if (inBatch >= 400) {
                        batch.commit().await()
                        batch = db.batch()
                        inBatch = 0
                    }
                }
                if (inBatch > 0) batch.commit().await()
                onDone(n)
            } catch (e: Exception) {
                onDone(-1)
            }
        }
    }
}

data class Idea(val id: String, val text: String, val votes: Int, val voters: List<String>, val author: String, val status: String)
