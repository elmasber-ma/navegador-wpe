package ma.elmasber.navegadorwpe

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

// Navegador v2: barra URL + pestañas + menú sobre GeckoView. UI 100% propia;
// el motor (GeckoSession/GeckoRuntime) se USA como librería externa.
class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME = "https://duckduckgo.com"
    }

    private data class Pestana(
        val vista: GeckoView,
        val sesion: GeckoSession,
        val boton: Button,
        var puedeAtras: Boolean = false,
        var puedeAdelante: Boolean = false,
    )

    private lateinit var runtime: GeckoRuntime
    private lateinit var contenedor: FrameLayout
    private lateinit var tiraPestanas: LinearLayout
    private lateinit var barraUrl: EditText
    private lateinit var progreso: ProgressBar
    private lateinit var btnAtras: Button
    private lateinit var btnAdelante: Button

    private val pestanas = mutableListOf<Pestana>()
    private var actual = -1

    private fun activa(): Pestana? = pestanas.getOrNull(actual)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        runtime = GeckoRuntime.create(this)

        contenedor = findViewById(R.id.contenedor)
        tiraPestanas = findViewById(R.id.tiraPestanas)
        barraUrl = findViewById(R.id.barraUrl)
        progreso = findViewById(R.id.progreso)
        btnAtras = findViewById(R.id.btnAtras)
        btnAdelante = findViewById(R.id.btnAdelante)

        findViewById<Button>(R.id.btnNueva).setOnClickListener { nuevaPestana(HOME) }
        findViewById<Button>(R.id.btnIr).setOnClickListener { irDesdeBarra() }
        barraUrl.setOnEditorActionListener { _, accion, _ ->
            if (accion == EditorInfo.IME_ACTION_GO) {
                irDesdeBarra()
                true
            } else {
                false
            }
        }
        btnAtras.setOnClickListener { activa()?.sesion?.goBack() }
        btnAdelante.setOnClickListener { activa()?.sesion?.goForward() }
        findViewById<Button>(R.id.btnRecargar).setOnClickListener { activa()?.sesion?.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { activa()?.sesion?.loadUri(HOME) }
        findViewById<Button>(R.id.btnCerrar).setOnClickListener { confirmarCerrarEn(actual) }
        findViewById<Button>(R.id.btnLog).setOnClickListener { mostrarLog() }

        nuevaPestana(HOME)
    }

    private fun normalizarUrl(texto: String): String {
        val t = texto.trim()
        if (t.isEmpty()) return HOME
        if (t.contains(" ") || (!t.contains(".") && !t.contains("://"))) {
            return "https://duckduckgo.com/?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        if (!t.contains("://")) return "https://$t"
        return t
    }

    private fun irDesdeBarra() {
        val p = activa() ?: return
        p.sesion.loadUri(normalizarUrl(barraUrl.text.toString()))
        barraUrl.clearFocus()
    }

    private fun nuevaPestana(url: String) {
        val sesion = GeckoSession()
        val vista = GeckoView(this)
        sesion.open(runtime)
        vista.setSession(sesion)

        sesion.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                permisos: List<GeckoSession.PermissionDelegate.ContentPermission>,
                gesto: Boolean,
            ) {
                runOnUiThread {
                    if (session == activa()?.sesion && url != null) barraUrl.setText(url)
                }
            }

            override fun onCanGoBack(session: GeckoSession, valor: Boolean) {
                pestanas.firstOrNull { it.sesion == session }?.puedeAtras = valor
                runOnUiThread { refrescarMenu() }
            }

            override fun onCanGoForward(session: GeckoSession, valor: Boolean) {
                pestanas.firstOrNull { it.sesion == session }?.puedeAdelante = valor
                runOnUiThread { refrescarMenu() }
            }
        })
        sesion.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runOnUiThread { progreso.visibility = View.VISIBLE }
            }

            override fun onPageStop(session: GeckoSession, ok: Boolean) {
                runOnUiThread {
                    progreso.visibility = View.GONE
                    refrescarMenu()
                }
            }

            override fun onProgressChange(session: GeckoSession, valor: Int) {
                runOnUiThread {
                    progreso.progress = valor
                    if (valor >= 100) progreso.visibility = View.GONE
                }
            }
        })
        sesion.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, titulo: String?) {
                runOnUiThread {
                    val p = pestanas.firstOrNull { it.sesion == session }
                    if (p != null && titulo != null) {
                        p.boton.text = if (titulo.length > 14) titulo.take(14) + "…" else titulo
                    }
                }
            }
        })

        val boton = Button(this)
        boton.text = "…"
        boton.textSize = 11f
        boton.setOnClickListener { cambiarA(pestanas.indexOfFirst { it.sesion == sesion }) }
        boton.setOnLongClickListener {
            cambiarA(pestanas.indexOfFirst { it.sesion == sesion })
            confirmarCerrarEn(actual)
            true
        }
        tiraPestanas.addView(boton)
        pestanas.add(Pestana(vista, sesion, boton))
        cambiarA(pestanas.size - 1)
        sesion.loadUri(url)
    }

    private fun cambiarA(i: Int) {
        if (i < 0 || i >= pestanas.size) return
        val anterior = activa()
        if (anterior != null) {
            anterior.vista.releaseSession()
            contenedor.removeView(anterior.vista)
        }
        actual = i
        val p = pestanas[i]
        p.vista.setSession(p.sesion)
        contenedor.addView(
            p.vista,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        barraUrl.setText("")
        refrescarMenu()
        tiraPestanas.post {
            for ((idx, q) in pestanas.withIndex()) {
                q.boton.alpha = if (idx == actual) 1.0f else 0.5f
            }
        }
    }

    private fun refrescarMenu() {
        val p = activa()
        btnAtras.isEnabled = p?.puedeAtras == true
        btnAdelante.isEnabled = p?.puedeAdelante == true
    }

    private fun confirmarCerrarEn(i: Int) {
        if (i < 0 || i >= pestanas.size) return
        AlertDialog.Builder(this)
            .setMessage("¿Cerrar esta pestaña?")
            .setPositiveButton("Cerrar") { _, _ ->
                val p = pestanas.removeAt(i)
                tiraPestanas.removeView(p.boton)
                contenedor.removeView(p.vista)
                p.vista.releaseSession()
                p.sesion.close()
                if (pestanas.isEmpty()) {
                    actual = -1
                    nuevaPestana(HOME)
                } else {
                    actual = -1
                    cambiarA(i.coerceAtMost(pestanas.size - 1))
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun mostrarLog() {
        Thread {
            val texto = try {
                val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "brief"))
                val out = p.inputStream.bufferedReader().readText()
                p.waitFor()
                val lineas = out.lines()
                val utiles = lineas.filter { l ->
                    l.contains("gecko", true) || l.contains("Gecko") || l.contains("navegadorwpe") ||
                        l.contains("FATAL") || l.contains("lowmemory")
                }
                (if (utiles.size > 400) utiles.takeLast(400) else utiles).joinToString("\n")
                    .ifEmpty { "(sin líneas del motor; últimas 100)\n" + lineas.takeLast(100).joinToString("\n") }
            } catch (e: Exception) {
                "no se pudo leer logcat: $e"
            }
            runOnUiThread {
                val tv = android.widget.TextView(this)
                tv.text = texto
                tv.textSize = 10f
                tv.setTextIsSelectable(true)
                tv.setPadding(16, 16, 16, 16)
                val sv = android.widget.ScrollView(this)
                sv.addView(tv)
                AlertDialog.Builder(this)
                    .setTitle("Log del motor (pegámelo)")
                    .setView(sv)
                    .setPositiveButton("Copiar") { _, _ ->
                        val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("gecko-log", texto))
                    }
                    .setNegativeButton("Cerrar", null)
                    .show()
            }
        }.start()
    }

    override fun onBackPressed() {
        val p = activa()
        if (p?.puedeAtras == true) {
            p.sesion.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        for (p in pestanas) {
            try {
                p.vista.releaseSession()
                p.sesion.close()
            } catch (_: Exception) {
            }
        }
        pestanas.clear()
        super.onDestroy()
    }
}
