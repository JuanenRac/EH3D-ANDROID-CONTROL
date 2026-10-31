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
import es.electrohobby3d.eh3d.model.Job
import es.electrohobby3d.eh3d.model.JobStates
import es.electrohobby3d.eh3d.model.JobSummary
import es.electrohobby3d.eh3d.model.Measure
import es.electrohobby3d.eh3d.network.ApiException
import es.electrohobby3d.eh3d.network.Eh3dApi
import es.electrohobby3d.eh3d.network.Login
import es.electrohobby3d.eh3d.network.Roles
import kotlinx.coroutines.Job as Task
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val stack = mutableStateListOf<Page>(Page.Home)
    private var visible = true
    private var poller: Task? = null
    private var lastStates = emptyMap<String, String>()

    val page: Page get() = stack.last()
    val canOperate: Boolean get() = Roles.canOperate(login?.role.orEmpty())
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
        job = null; head = null; measure = null; noHead = false
        refreshNow()
    }

    fun tab(page: Page) { stack.clear(); stack.add(Page.Home); if (page != Page.Home) stack.add(page); job = null; head = null; refreshNow() }

    /** Back one page; false when there is none (the app may close). */
    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.size - 1)
        job = null; head = null; measure = null; noHead = false
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

    fun measureTable(machineId: String) = act { measure = api.measure(machineId) }

    fun chooseLanguage(code: String) { Tr.choose(getApplication(), code) }

    private fun errorText(error: ApiException): String = when {
        error.status == 0 -> tr("No se alcanza el servidor. Compruebe la dirección y que el teléfono está en la misma red.")
        error.code == "invalid_credentials" || error.status == 401 -> tr("Usuario o contraseña incorrectos.")
        error.code == "throttled" -> tr("Demasiados intentos: espere un momento.")
        error.code == "machine_busy" -> tr("La máquina ya está trabajando.")
        error.code == "no_head" -> tr("Esta máquina no tiene nodo de cabezal.")
        error.status == 403 -> tr("Su usuario solo puede ver: no puede hacer esto.")
        else -> trf("El servidor respondió: %s", error.message ?: error.code)
    }
}
