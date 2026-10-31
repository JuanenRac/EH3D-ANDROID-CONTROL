// EH3D-ANDROID-CONTROL - the state of the app: who is signed in, the fleet as the server reports it, the job and the camera of the machine that is open, and what the buttons do.
// Copyright (C) 2026 JuanenRac (Electro Hobby 3D). GPL-3.0-or-later.
package es.electrohobby3d.eh3d

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.electrohobby3d.eh3d.model.Fleet
import es.electrohobby3d.eh3d.model.HeadStatus
import es.electrohobby3d.eh3d.model.Quality
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobStates
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.JobView
import es.electrohobby3d.eh3d.model.Measure
import es.electrohobby3d.eh3d.model.Prepare
import es.electrohobby3d.eh3d.model.Side
import es.electrohobby3d.eh3d.model.SideState
import es.electrohobby3d.eh3d.model.Work
import es.electrohobby3d.eh3d.model.SpindleInfo
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import es.electrohobby3d.eh3d.network.Login
import es.electrohobby3d.eh3d.network.Roles
import kotlinx.coroutines.Job as Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** The pages of the app; the back button walks back through the ones that were opened. */
sealed interface Page {
    data object Home : Page
    data object Jobs : Page
    data object More : Page
    data object Help : Page
    data object About : Page
    data object Languages : Page
    data class Machine(val id: String) : Page
    data class JobPage(val id: String) : Page
    /** The page that prepares a job on a machine: what to do, the file, its tools and where to start. */
    data class Prepare(val machine: String) : Page
}

/** What the page that prepares a job holds: the work chosen, the file of each side with what the server planned from it, and the free G-code program. */
data class PrepareUi(
    val work: Work = Work.Drill,
    val drill: SideState = SideState(), val route: SideState = SideState(),
    val gcodeName: String = "", val gcode: String = "", val skip: Int = 0,
    /** Start the spindle and the coolant again when a program goes on after a stop: off unless the person turns it on. */
    val restoreOutputs: Boolean = false,
    val gcodeView: JobView? = null, val gcodeError: String = "",
    val loading: Boolean = false, val busy: Boolean = false,
) {
    fun side(side: Side) = if (side == Side.Drill) drill else route
    fun with(side: Side, state: SideState) = if (side == Side.Drill) copy(drill = state) else copy(route = state)
}

enum class Phase { Booting, SignedOut, FirstSetup, SignedIn }

/** The four moments the sign-in goes through, for the screen to say what it is doing. */
enum class Busy { No, Connecting, Working }

class Eh3dViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("eh3d-control", Context.MODE_PRIVATE)
    private val store = PreferencesStore(preferences)
    private val box = KeystoreBox()
    private val sessions = SessionVault(store, box)
    private val credentials = CredentialVault(store, box)
    private var api = Eh3dApi("")

    var phase by mutableStateOf(Phase.Booting); private set
    var busy by mutableStateOf(Busy.No); private set
    var origin by mutableStateOf(preferences.getString("origin", "") ?: ""); private set
    var login: Login? by mutableStateOf(null); private set
    var fleet by mutableStateOf(Fleet.EMPTY); private set
    /** True while the server does not answer: what is on the screen is the last that was known. */
    var offline by mutableStateOf(false); private set
    var message: String? by mutableStateOf(null); private set
    var rememberMe by mutableStateOf(credentials.has()); private set
    var jobs: List<JobSummary> by mutableStateOf(emptyList()); private set
    var job: Job? by mutableStateOf(null); private set
    var head: HeadStatus? by mutableStateOf(null); private set
    var noHead by mutableStateOf(false); private set
    var measure: Measure? by mutableStateOf(null); private set
    /** The last quality check of the machine on screen, and whether one is running now. */
    var quality: Quality? by mutableStateOf(null); private set
    var qualityBusy by mutableStateOf(false); private set
    private var qualityLooked = false
    /** The spindle of the machine on screen (null until the server has answered). */
    var spindle: SpindleInfo? by mutableStateOf(null); private set
    private var spindleLooked = false
    /** The job being prepared, and the text of the G-code programs started from here (to go on from the line where one stopped without choosing the file again). */
    var prepare by mutableStateOf(PrepareUi()); private set
    private val gcodeSources = HashMap<String, Pair<String, String>>()
    private var previewing: Task? = null
    val stack = mutableStateListOf<Page>(Page.Home)
    private var visible = true
    private var poller: Task? = null
    private var lastStates = emptyMap<String, String>()

    val page: Page get() = stack.last()
    val canOperate: Boolean get() = Roles.canOperate(login?.role.orEmpty())
    val canAdmin: Boolean get() = Roles.canAdmin(login?.role.orEmpty())
    val streamUrl: (String) -> String get() = { api.streamUrl(it) }

    init { start() }

    fun savedOrigin(): String = origin
    fun savedUser(): String = credentials.load(origin)?.username.orEmpty()
    fun savedPassword(): String = credentials.load(origin)?.password.orEmpty()

    private fun start() {
        viewModelScope.launch {
            if (origin.isBlank()) { phase = Phase.SignedOut; return@launch }
            api = Eh3dApi(origin)
            val kept = sessions.load(origin)
            if (kept != null) {
                api.token = kept.value
                try { login = api.session(); phase = Phase.SignedIn; poll(); return@launch } catch (_: ApiException) { sessions.clear(); api.token = "" }
            }
            val saved = credentials.load(origin)
            if (saved != null) {
                try { doSignIn(origin, saved.username, saved.password, true); return@launch } catch (_: Exception) { /* the sign-in screen asks again */ }
            }
            phase = Phase.SignedOut
        }
    }

    /** Looks at the server before asking for a password: does it need its first administrator? */
    fun connect(host: String, port: String, then: (Boolean) -> Unit) {
        val endpoint = ServerEndpoint.fromHostAndPort(host, port)
        if (endpoint == null) { message = tr("La dirección del servidor no es válida (use una dirección de la red local o https://)."); return }
        viewModelScope.launch {
            busy = Busy.Connecting
            try {
                val probe = Eh3dApi(endpoint.origin)
                val needed = probe.setupNeeded()
                api = probe
                origin = endpoint.origin
                preferences.edit().putString("origin", origin).apply()
                phase = if (needed) Phase.FirstSetup else Phase.SignedOut
                message = null
                then(needed)
            } catch (error: ApiException) { message = errorText(error) } finally { busy = Busy.No }
        }
    }

    fun firstSetup(code: String, name: String, password: String) {
        viewModelScope.launch {
            busy = Busy.Working
            try { api.setup(code.trim(), name.trim(), password); doSignIn(origin, name.trim(), password, false) } catch (error: ApiException) { message = errorText(error) } finally { busy = Busy.No }
        }
    }

    fun signIn(address: String, name: String, password: String, remember: Boolean) {
        viewModelScope.launch {
            busy = Busy.Working
            try { doSignIn(address, name, password, remember) } catch (error: ApiException) { message = errorText(error) } finally { busy = Busy.No }
        }
    }

    private suspend fun doSignIn(address: String, name: String, password: String, remember: Boolean) {
        if (api.token.isEmpty() && origin != address) { api = Eh3dApi(address); origin = address }
        val result = api.login(name.trim(), password)
        api.token = result.token
        login = result
        sessions.save(origin, "token", result.token)
        if (remember) credentials.save(origin, name.trim(), password) else credentials.clear()
        rememberMe = remember
        preferences.edit().putString("origin", origin).apply()
        message = null
        phase = Phase.SignedIn
        poll()
    }

    fun signOut() {
        poller?.cancel()
        viewModelScope.launch { api.logout() }
        sessions.clear()
        credentials.clear()
        rememberMe = false
        api.token = ""
        login = null
        fleet = Fleet.EMPTY
        job = null; head = null; jobs = emptyList()
        stack.clear(); stack.add(Page.Home)
        phase = Phase.SignedOut
    }

    fun dismissMessage() { message = null }
    fun setVisible(value: Boolean) { visible = value; if (value && phase == Phase.SignedIn) poll() }

    // ---- the pages
    fun open(page: Page) {
        if (page == this.page) return
        stack.add(page)
        job = null; head = null; measure = null; quality = null; qualityLooked = false; spindle = null; spindleLooked = false; noHead = false
        refreshNow()
    }

    fun tab(page: Page) { stack.clear(); stack.add(Page.Home); if (page != Page.Home) stack.add(page); job = null; head = null; refreshNow() }

    /** Back one page; false when there is none (the app may close). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        job = null; head = null; measure = null; quality = null; qualityLooked = false; spindle = null; spindleLooked = false; noHead = false
        refreshNow()
        return true
    }

    // ---- the watch: the fleet every second and a half while the app is in sight, and what the open page needs
    private fun poll() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (phase == Phase.SignedIn) {
                if (visible) refresh()
                delay(if (visible) 1_500 else 10_000)
            }
        }
    }

    private fun refreshNow() { if (phase == Phase.SignedIn) viewModelScope.launch { refresh() } }

    private suspend fun refresh() {
        try {
            val now = api.fleet()
            raise(now)
            fleet = now
            offline = false
            when (val p = page) {
                is Page.Machine -> {
                    val active = now.machine(p.id)?.job
                    if (active != null) job = api.job(active.id) else if (job?.summary?.machine != p.id) job = null
                    if (!noHead && !qualityLooked && !qualityBusy) { qualityLooked = true; quality = try { api.lastInspection(p.id) } catch (error: ApiException) { null } }
                    if (!spindleLooked) { spindleLooked = true; spindle = try { api.spindle(p.id) } catch (error: ApiException) { if (error.unauthorized) throw error else null } }
                    if (!noHead) try { head = api.head(p.id) } catch (error: ApiException) { if (error.code == "no_head") { noHead = true; head = null } }
                    jobs = api.jobs(p.id)
                }
                is Page.JobPage -> job = api.job(p.id)
                Page.Jobs -> jobs = api.jobs()
                else -> Unit
            }
        } catch (error: ApiException) {
            if (error.unauthorized) { sessions.clear(); signOutQuietly() } else offline = true
        }
    }

    private fun signOutQuietly() { api.token = ""; login = null; phase = Phase.SignedOut; poller?.cancel() }

    /** A machine that stops, finishes or waits for the operator is told in a notice, once. */
    private fun raise(now: Fleet) {
        val current = now.machines.associate { it.id to (it.job?.state ?: it.state) }
        if (lastStates.isNotEmpty()) for (m in now.machines) {
            val before = lastStates[m.id] ?: continue
            val after = current.getValue(m.id)
            if (before != after && after == "waiting_operator") Notifier.show(getApplication(), m.id.hashCode(), m.name, tr("Espera al operario: confirme el paso en la máquina."))
            if (before != after && m.job == null && JobStates.active(before)) Notifier.show(getApplication(), m.id.hashCode(), m.name, tr("El trabajo ha terminado o se ha detenido."))
        }
        lastStates = current
    }

    // ---- what the buttons do
    private fun act(work: suspend () -> Unit) {
        viewModelScope.launch {
            try { work(); refresh() } catch (error: ApiException) { if (error.unauthorized) signOutQuietly() else message = errorText(error) }
        }
    }

    fun start(id: String) = act { api.start(id) }
    fun confirm(id: String) = act { api.confirm(id) }
    fun cancel(id: String) = act { api.cancel(id) }
    fun delete(id: String) = act { api.delete(id); if (page is Page.JobPage) back() }

    /** The emergency stop of one machine: its running job is cancelled, which stops the machine and switches the drivers off. */
    fun emergencyStop(machineId: String) {
        val id = fleet.machine(machineId)?.job?.id ?: return
        cancel(id)
    }

    /** The emergency stop of every machine that is working. */
    fun emergencyStopAll() { fleet.machines.mapNotNull { it.job?.id }.forEach { cancel(it) } }

    fun pause(id: String) = act { api.pause(id) }
    fun resume(id: String) = act { api.resume(id) }

    // ---- preparing a job
    /** True when the text of the program that [id] ran is still known, so it can go on from its line without choosing the file again. */
    fun knowsProgram(id: String) = gcodeSources.containsKey(id)

    /** Opens the page that prepares a job on a machine, with the first work its connection takes. */
    fun openPrepare(machineId: String) {
        val connection = fleet.machine(machineId)?.connection.orEmpty()
        open(Page.Prepare(machineId))
        prepare = PrepareUi(work = Prepare.works(connection).firstOrNull() ?: Work.Drill)
    }

    /** Goes on with a G-code job that failed or was stopped: the same program from the line it says (the text is asked for again when this session does not have it). */
    fun openResume(summary: JobSummary) {
        val source = gcodeSources[summary.id]
        val text = source?.second.orEmpty()
        open(Page.Prepare(summary.machine))
        prepare = PrepareUi(work = Work.Gcode, gcodeName = source?.first.orEmpty(), gcode = text, skip = Prepare.resumeSkip(summary.line, if (text.isEmpty()) summary.line else Prepare.lineCount(text)))
        if (text.isNotEmpty()) previewGcode(summary.machine)
    }

    fun setWork(work: Work) { prepare = prepare.copy(work = work) }
    fun setSkip(machineId: String, skip: Int) { prepare = prepare.copy(skip = skip.coerceAtLeast(0)); if (prepare.gcode.isNotEmpty()) previewGcode(machineId) }
    fun setRestoreOutputs(value: Boolean) { prepare = prepare.copy(restoreOutputs = value) }

    fun toggleToolOff(side: Side, tool: Int) {
        val st = prepare.side(side)
        val off = Prepare.toggleOff(st.off, tool)
        prepare = prepare.with(side, st.copy(off = off, startAt = if (tool in off && st.startAt == tool) null else st.startAt))
    }

    fun toggleStartAt(side: Side, tool: Int) {
        val st = prepare.side(side)
        prepare = prepare.with(side, st.copy(startAt = Prepare.toggleStartAt(st.startAt, tool, st.off)))
    }

    private data class Picked(val name: String, val text: String)

    private suspend fun read(uri: android.net.Uri): Picked = withContext(Dispatchers.IO) {
        val resolver = getApplication<Application>().contentResolver
        var name = ""
        resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) name = it.getString(0).orEmpty() }
        val out = java.io.ByteArrayOutputStream()
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > Prepare.MAX_FILE_BYTES) throw ApiException(0, "too_big", "too_big")
            }
        }
        Picked(name, String(out.toByteArray(), Charsets.UTF_8).replace("\r\n", "\n"))
    }

    /** The file of drilling (`.NCD`) or routing (`.PLT`): it is read, sent to the server to plan (nothing is created) and the tools it uses come back with their count. */
    fun pickFile(machineId: String, side: Side, uri: android.net.Uri) {
        viewModelScope.launch {
            prepare = prepare.copy(loading = true)
            try {
                val picked = read(uri)
                val old = prepare.side(side)
                prepare = prepare.with(side, SideState(fileName = picked.name, text = picked.text, tableName = old.tableName, tableText = old.tableText))
                planSide(machineId, side)
            } catch (error: ApiException) { prepare = prepare.with(side, prepare.side(side).copy(error = prepareError(error))) }
            catch (_: Exception) { prepare = prepare.with(side, prepare.side(side).copy(error = tr("No se pudo leer el archivo."))) }
            finally { prepare = prepare.copy(loading = false) }
        }
    }

    /** The tool table of a side (`.DTD` or `.RTD`), optional. */
    fun pickTable(machineId: String, side: Side, uri: android.net.Uri) {
        viewModelScope.launch {
            prepare = prepare.copy(loading = true)
            try {
                val picked = read(uri)
                prepare = prepare.with(side, prepare.side(side).copy(tableName = picked.name, tableText = picked.text))
                if (prepare.side(side).hasFile) planSide(machineId, side)
            } catch (error: ApiException) { prepare = prepare.with(side, prepare.side(side).copy(error = prepareError(error))) }
            catch (_: Exception) { prepare = prepare.with(side, prepare.side(side).copy(error = tr("No se pudo leer el archivo."))) }
            finally { prepare = prepare.copy(loading = false) }
        }
    }

    private suspend fun planSide(machineId: String, side: Side) {
        val st = prepare.side(side)
        try {
            val view = api.preview(machineId, Prepare.fileBody(side, Prepare.jobName(st.fileName), st.text, st.tableText, emptyList()))
            prepare = prepare.with(side, prepare.side(side).copy(view = view, error = ""))
        } catch (error: ApiException) {
            if (error.unauthorized) signOutQuietly()
            prepare = prepare.with(side, prepare.side(side).copy(view = null, error = prepareError(error)))
        }
    }

    /** The G-code program: its text is read and planned from the line [PrepareUi.skip]. */
    fun pickProgram(machineId: String, uri: android.net.Uri) {
        viewModelScope.launch {
            prepare = prepare.copy(loading = true)
            try {
                val picked = read(uri)
                prepare = prepare.copy(gcodeName = picked.name, gcode = picked.text, gcodeView = null, gcodeError = "")
                previewGcode(machineId)
            } catch (error: ApiException) { prepare = prepare.copy(gcodeError = prepareError(error)) }
            catch (_: Exception) { prepare = prepare.copy(gcodeError = tr("No se pudo leer el archivo.")) }
            finally { prepare = prepare.copy(loading = false) }
        }
    }

    private fun previewGcode(machineId: String) {
        previewing?.cancel()
        previewing = viewModelScope.launch {
            val p = prepare
            try {
                val view = api.preview(machineId, Prepare.gcodeBody(Prepare.jobName(p.gcodeName), p.gcode, p.skip, p.restoreOutputs))
                prepare = prepare.copy(gcodeView = view, gcodeError = "")
            } catch (error: ApiException) {
                if (error.unauthorized) signOutQuietly()
                prepare = prepare.copy(gcodeView = null, gcodeError = prepareError(error))
            }
        }
    }

    /** Creates the job (or the two of a drilling and routing pair) with the tools switched off and the tool to start at, and starts the first one. The second stays ready: a job never starts by itself. */
    fun startPrepared(machineId: String) {
        val p = prepare
        if (p.busy) return
        viewModelScope.launch {
            prepare = prepare.copy(busy = true)
            var made = 0
            try {
                val jobs = mutableListOf<Job>()
                if (p.work == Work.Gcode) {
                    val job = api.createJob(machineId, Prepare.gcodeBody(Prepare.jobName(p.gcodeName), p.gcode, p.skip, p.restoreOutputs))
                    gcodeSources[job.summary.id] = p.gcodeName to p.gcode
                    jobs += job
                    made++
                } else {
                    val sides = when (p.work) { Work.Drill -> listOf(Side.Drill); Work.Route -> listOf(Side.Route); else -> listOf(Side.Drill, Side.Route) }
                    for (side in sides) {
                        val st = p.side(side)
                        val skipTools = Prepare.skipTools(st.view?.let { Prepare.toolOrder(it) }.orEmpty(), st.off, st.startAt)
                        jobs += api.createJob(machineId, Prepare.fileBody(side, Prepare.jobName(st.fileName), st.text, st.tableText, skipTools))
                        made++
                    }
                }
                api.start(jobs.first().summary.id)
                message = if (jobs.size > 1) tr("El primer trabajo está en marcha. El segundo queda listo: arránquelo desde la pestaña Trabajos cuando termine.") else null
                if (page is Page.Prepare) back()
                refresh()
            } catch (error: ApiException) {
                if (error.unauthorized) signOutQuietly() else message = prepareError(error) + if (made > 0) " " + tr("Lo que ya se creó queda listo en la pestaña Trabajos.") else ""
            } finally { prepare = prepare.copy(busy = false) }
        }
    }

    private fun prepareError(error: ApiException): String = when (error.code) {
        "too_big" -> tr("El archivo es demasiado grande.")
        "bad_gcode" -> trf("Hay una línea que no es G-code válido: %s", error.message ?: "")
        "bad_ncd" -> tr("El archivo de taladrado no se entiende.")
        "unknown_tool" -> trf("Una herramienta del archivo no está en la tabla de herramientas de la máquina: %s", error.message ?: "")
        "bad_tool" -> trf("Herramienta no válida: %s", error.message ?: "")
        "out_of_table" -> tr("Un agujero o un punto queda fuera del recorrido de la máquina.")
        "wrong_machine" -> tr("Esta máquina no admite esa clase de trabajo.")
        else -> errorText(error)
    }

    fun measureTable(machineId: String) = act { measure = api.measure(machineId) }

    /** The ring of LEDs around the camera of the drill head: off (0) or on at a level. */
    fun setLight(machineId: String, level: Int) = act { api.setLight(machineId, level) }

    /** The quality check of the work through the camera: the verdict comes back and stays on screen until the next one. */
    fun inspect(machineId: String, light: Int, holeMm: Double) {
        if (qualityBusy) return
        qualityBusy = true
        viewModelScope.launch {
            try { quality = api.inspect(machineId, light, holeMm); refresh() }
            catch (error: ApiException) { if (error.unauthorized) signOutQuietly() else message = errorText(error) }
            finally { qualityBusy = false }
        }
    }

    fun inspectPreset(machineId: String, index: Int) = act { api.inspectPreset(machineId, index) }

    fun inspectCalibrate(machineId: String) = act { api.inspectCalibrate(machineId); quality = null; qualityLooked = true }

    /** Chooses the spindle of the machine (`original`, `vfd` or empty for none) and/or what the server does with a job that asks for more (`reject` or `warn`). */
    fun setSpindle(machineId: String, spindle: String?, guard: String?) = act { this.spindle = api.setSpindle(machineId, spindle, guard) }

    fun chooseLanguage(code: String) { Tr.choose(getApplication(), code) }

    private fun errorText(error: ApiException): String = when {
        error.status == 0 -> tr("No se alcanza el servidor. Compruebe la dirección y que el teléfono está en la misma red.")
        error.code == "invalid_credentials" || error.status == 401 -> tr("Usuario o contraseña incorrectos.")
        error.code == "throttled" -> tr("Demasiados intentos: espere un momento.")
        error.code == "machine_busy" -> tr("La máquina ya está trabajando.")
        error.code == "no_head" -> tr("Esta máquina no tiene nodo de cabezal.")
        error.code == "no_light" -> tr("Este nodo no tiene aro de luz.")
        error.code == "no_inspection" -> tr("Verifica primero un agujero: la escala sale de su ancho.")
        error.code == "no_hole" -> tr("La última verificación no encontró ningún agujero con el que medir la escala.")
        error.code == "spindle_limit" -> tr("El trabajo pide más de lo que da el husillo de la máquina.")
        error.code == "bad_spindle" -> tr("Husillo o límites no válidos.")
        error.code == "not_pausable" -> tr("Solo se puede pausar un trabajo de G-code.")
        error.code == "not_in_state" -> tr("El trabajo ya no está en un estado que permita eso.")
        error.code == "no_work" -> tr("No queda nada por hacer: todas las herramientas están desactivadas o el trabajo ya está hecho.")
        error.code == "head_unreachable" -> tr("El nodo del cabezal no responde.")
        error.status == 403 -> tr("Su usuario solo puede ver: no puede hacer esto.")
        else -> trf("El servidor respondió: %s", error.message ?: error.code)
    }
}
