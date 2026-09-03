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
import org.wpewebkit.wpe.WKWebView
import org.wpewebkit.wpeview.WPEChromeClient
import org.wpewebkit.wpeview.WPEView
import org.wpewebkit.wpeview.WPEViewClient

// Navegador v1: barra URL + pestañas + menú. UI 100% propia.
// Motor 0.3.3 (compilado): WPEView = surface (crea su propio contexto),
// API real en WKWebView vía getWKWebView().
class MainActivity : AppCompatActivity() {

    companion object {
        const val HOME = "https://duckduckgo.com"
    }

    private data class Pestana(val vista: WPEView, val wk: WKWebView, val boton: Button)

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
        btnAtras.setOnClickListener { activa()?.wk?.goBack() }
        btnAdelante.setOnClickListener { activa()?.wk?.goForward() }
        findViewById<Button>(R.id.btnRecargar).setOnClickListener { activa()?.wk?.reload() }
        findViewById<Button>(R.id.btnHome).setOnClickListener { activa()?.wk?.loadUrl(HOME) }
        findViewById<Button>(R.id.btnCerrar).setOnClickListener { confirmarCerrarEn(actual) }

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
        activa()?.wk?.loadUrl(normalizarUrl(barraUrl.text.toString()))
    }

    private fun nuevaPestana(url: String) {
        val vista = WPEView(this)
        val wk = vista.wkWebView
        wk.setWPEViewClient(object : WPEViewClient() {
            override fun onPageStarted(view: WPEView, url: String) {
                runOnUiThread {
                    progreso.visibility = View.VISIBLE
                    if (view == activa()?.vista) barraUrl.setText(url)
                }
            }

            override fun onPageFinished(view: WPEView, url: String) {
                runOnUiThread {
                    progreso.visibility = View.GONE
                    refrescarMenu()
                    if (view == activa()?.vista) barraUrl.setText(url)
                }
            }
        })
        wk.setWPEChromeClient(object : WPEChromeClient {
            override fun onProgressChanged(view: WPEView, nuevo: Int) {
                runOnUiThread {
                    progreso.progress = nuevo
                    if (nuevo >= 100) progreso.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WPEView, titulo: String) {
                runOnUiThread {
                    val p = pestanas.firstOrNull { it.vista == view }
                    if (p != null) {
                        p.boton.text = if (titulo.length > 14) titulo.take(14) + "…" else titulo
                    }
                }
            }
        })

        val boton = Button(this)
        boton.text = "…"
        boton.textSize = 11f
        boton.setOnClickListener { cambiarA(pestanas.indexOfFirst { it.vista == vista }) }
        boton.setOnLongClickListener {
            cambiarA(pestanas.indexOfFirst { it.vista == vista })
            confirmarCerrarEn(actual)
            true
        }
        tiraPestanas.addView(boton)
        pestanas.add(Pestana(vista, wk, boton))
        cambiarA(pestanas.size - 1)
        wk.loadUrl(url)
    }

    private fun cambiarA(i: Int) {
        if (i < 0 || i >= pestanas.size) return
        actual = i
        val p = pestanas[i]
        contenedor.removeAllViews()
        contenedor.addView(p.vista)
        barraUrl.setText(p.wk.url ?: "")
        refrescarMenu()
        tiraPestanas.post {
            for ((idx, q) in pestanas.withIndex()) {
                q.boton.alpha = if (idx == actual) 1.0f else 0.5f
            }
        }
    }

    private fun refrescarMenu() {
        val w = activa()?.wk
        btnAtras.isEnabled = w?.canGoBack() == true
        btnAdelante.isEnabled = w?.canGoForward() == true
    }

    private fun confirmarCerrarEn(i: Int) {
        if (i < 0 || i >= pestanas.size) return
        AlertDialog.Builder(this)
            .setMessage("¿Cerrar esta pestaña?")
            .setPositiveButton("Cerrar") { _, _ ->
                val p = pestanas.removeAt(i)
                tiraPestanas.removeView(p.boton)
                contenedor.removeView(p.vista)
                p.vista.destroy()
                if (pestanas.isEmpty()) {
                    nuevaPestana(HOME)
                } else {
                    cambiarA(i.coerceAtMost(pestanas.size - 1))
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onBackPressed() {
        val w = activa()?.wk
        if (w?.canGoBack() == true) {
            w.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        for (p in pestanas) {
            try {
                p.vista.destroy()
            } catch (_: Exception) {
            }
        }
        pestanas.clear()
        super.onDestroy()
    }
}
