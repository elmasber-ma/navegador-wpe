# Generar SOLO el .lib (WPE WebKit para Android)

Objetivo: producir únicamente los `.so` (y recursos nativos) del motor,
para usarlos en cualquier proyecto, sin compilar ninguna UI.

## Opción A — bajarlos ya compilados (rápido, recomendado)

Los publica Igalia por release (mismo código que `WebKit/WebKit` rama WPE):

- Tarballs: `https://wpewebkit.org/android/bootstrap/{VERSION}/`
  - `wpewebkit-android-arm64-{VERSION}.tar.xz` (devel: headers + `.so`)
  - `wpewebkit-android-arm64-{VERSION}-runtime.tar.xz` (`.so` + plugins + recursos)
  - Igual para `x86_64` (emulador).
- Versión actual conocida: `2.53.3.8` (ver `default_version` en
  `tools/scripts/bootstrap.py` de `Igalia/wpe-android`).
- Vía Maven (AAR listo para Gradle):
  `implementation "org.wpewebkit.wpeview:wpeview:0.3.3"` (última 2026-03).

Qué hace el bootstrap tras descargar (si lo hacés a mano):
1. Extrae a `build/sysroot/<arch>`.
2. Renombra librerías versionadas (`libfoo.so.1` → `libfoo_1.so`,
   ajustando SONAME/NEEDED) porque el PackageManager solo instala
   `libxxx.so` pelados.
3. Reparte: headers → `cpp/imported/include`, `.so` de link →
   `cpp/imported/lib/<abi>`, resto → `jniLibs/<abi>`, plugins
   GStreamer → `assets/gstreamer-1.0/<abi>`, `libgioopenssl.so` →
   `assets/gio/<abi>`, `injected-bundle` + `inspector.gresource` →
   `assets/injected-bundles/<abi>`, `GStreamer.java` →
   `java/org/freedesktop/gstreamer`.

## Opción B — compilarlos desde fuentes (lento, máquina potente)

Requiere Linux x86_64, ~100 GB libres, 32 GB RAM, varias horas:

```bash
git clone https://github.com/Igalia/wpe-android.git
cd wpe-android
./tools/scripts/bootstrap.py --build -a arm64   # o x86_64 / all
```

Esto clona `wpe-android-cerbero`, compila TODAS las dependencias
(glib, GStreamer, libsoup…) y después WPE WebKit desde fuentes
upstream, empaquetando los mismos 2 tarballs de la opción A.
Flags extra: `--debug` (símbolos), `--cerbero <path>` (reusar build),
`--version` (fijar release). Deps del script: `git python3-distro
python3-venv ruby unifdef readelf tar` + Android NDK
(`./tools/scripts/install-android-ndk.sh`).

## Licencias del .lib

Los `.so` del motor son **LGPL-2.1** (licencia de WebKit, no de Igalia).
Al distribuir un APK que los incluya: link dinámico (archivos
separados), aviso + copia de licencia (ver `NOTICES`), fuente
disponible (basta apuntar a `WebKit/WebKit` + `Igalia/wpe-android`),
sin anti-tamper que bloquee reemplazarlos.
