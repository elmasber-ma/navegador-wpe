package ma.elmasber.navegadorwpe

import org.wpewebkit.WPEApplication

// Obligatorio: la app debe extender WPEApplication para que cargue
// los .so nativos en cada proceso (UI/red/contenido). Solo se USA la
// clase de la librería externa; este archivo es código propio.
class NavegadorApp : WPEApplication()
