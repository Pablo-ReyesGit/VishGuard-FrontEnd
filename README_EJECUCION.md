# Ejecutar VishGuard y crear una cuenta

Este repositorio es una aplicación Android nativa (Kotlin), no un sitio web.
Abrir la carpeta `VishGuard-FrontEnd` en Android Studio. Los cambios se hicieron
en la rama `Diego`; el backend permanece en `Diego`.

## 1. Encender el backend

En una terminal PowerShell:

```powershell
cd "C:\Users\josec\OneDrive\Escritorio\Universidad\10mo Semestre\Seminario\VISHGUARD_BD\VishGuard-BackEnd"
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn main:app --host 0.0.0.0 --port 8000 --reload --log-level warning
```

Dejar abierta esa terminal. Comprobar en el navegador de la PC:
`http://127.0.0.1:8000/docs`.

El backend toma `DATABASE_URL` de su `.env`. La migración de `user` y `alerta`
ya se aplicó a la base revisada en esta conversación. No volver a ejecutar `002`.
Las cuentas creadas desde la app se guardan en esa base.

## 2. Ejecutar Android

1. En Android Studio: **File > Open**, seleccionar esta carpeta.
2. Esperar la sincronización de Gradle y la descarga de componentes que solicite
   el IDE. Este proyecto usa SDK 37, Gradle 9.5 y el JDK 25 configurado por Gradle.
   Se compiló con el Java incluido en Android Studio de esta PC.
3. En **Device Manager**, iniciar `Pixel_8` (ya existe en esta PC), o seleccionar
   un teléfono conectado por USB con depuración USB habilitada.
4. Seleccionar la configuración **app** y pulsar **Run ▶**.

La primera pantalla permite iniciar sesión o crear una cuenta.

## 3. Dirección del backend

La dirección se puede cambiar en la pantalla de cuenta; todas las solicitudes
y la suscripción a alertas usan esa misma configuración.

| Dónde corre Android | Dirección que se escribe en la app |
| --- | --- |
| Emulador de Android Studio | `http://10.0.2.2:8000` |
| Teléfono por USB con redirección ADB | `http://127.0.0.1:8000` |
| Teléfono por Wi-Fi | `http://IP_DE_LA_PC:8000` |

El [emulador usa 10.0.2.2 para llegar a la PC](https://developer.android.com/studio/run/emulator-networking-address).
No agregar `/docs` ni `/api/v1` al campo de dirección.

Para redirigir el puerto al teléfono por USB, ejecutar en PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8000 tcp:8000
```

Por Wi-Fi, ambos equipos deben estar en la misma red. Consultar `ipconfig` en la
PC y usar el IPv4 del adaptador conectado; permitir el backend en la red privada
si Windows pregunta. `127.0.0.1` en el teléfono corresponde al propio teléfono.

Al cambiar de servidor, la app cierra la sesión anterior.

## 4. Crear la cuenta

Pulsar **Crear una cuenta** e ingresar:

- Nombre (opcional).
- Un correo válido.
- Contraseña de 8 a 128 caracteres.
- Teléfono personal que recibe la llamada, con código de país; por ejemplo,
  `+50212345678`. No usar aquí el número de Twilio.

La app registra la cuenta, inicia sesión y abre la pantalla de protección.
Desde el engranaje se puede actualizar el teléfono o cerrar sesión.
Si el teléfono ya pertenece a otra cuenta, el servidor responde con un error
visible. El número se registra para las pruebas; todavía no hay verificación SMS.
La contraseña no se guarda en el teléfono; el token y el perfil se cifran con
una clave del Android Keystore y se excluyen de las copias de seguridad.

## 5. Comprobar la integración

- La tarjeta **Análisis de hoy** cuenta respuestas válidas recibidas, tanto de
  simulaciones de texto como de la conexión en vivo. No cuenta solicitudes fallidas
  ni el estado inicial de protección. Un análisis de nivel `PELIGROSO` o `FRAUDE`
  suma una amenaza; `BAJO` y `MEDIO` suman solamente un análisis. Los contadores se
  guardan localmente por cuenta y servidor, se restauran al reabrir la app y
  comienzan en cero al cambiar de día. Cada fragmento analizado cuenta como un
  análisis: estos totales no representan un conteo de llamadas completas.
- En **Opciones > Historial**, se consulta `GET /alerts` usando el token del
  usuario. Una cuenta nueva mostrará un historial vacío.
- Al activar la protección, conceder el permiso de mostrar sobre otras apps.
  El servicio conecta a `/ws/stream` con el teléfono y el token.
- La opción de simular una frase llama a `/analizar-llamada` con el token del
  usuario. Cada evaluación válida se guarda en `alerta` con `es_simulacion=true`,
  incluso si el riesgo es bajo. La respuesta confirma `alerta_guardada=true` y
  devuelve `alerta_id`. El overlay confirma el guardado y el historial muestra
  la etiqueta **SIMULACIÓN**. Si falla el guardado, la app muestra el error.
  Es necesario tener aplicada la migración `003_simulation_alerts.sql` en bases
  existentes; ya se aplicó a la base de Neon usada en esta conversación.
- Para una llamada real, `VISHGUARD_DESTINO` del backend debe coincidir con el
  teléfono del perfil. La instancia del backend sigue usando un único destino.
  El número de Twilio se usa para recibir la llamada inicial y redirigirla.
- La transcripción real requiere las dependencias de STT del modo elegido por el
  equipo. Instalar el backend y registrar una cuenta no instala Whisper ni sus
  modelos. En modo STT `simulado` no se guardan alertas.

Para exigir autenticación y propiedad del teléfono en el WebSocket, configurar
`WS_AUTH_REQUIRED=true` en el `.env` del backend y reiniciarlo. La app ya envía el
token. No compartir el `.env` ni incluirlo en el proyecto Android.

## Compilación y pruebas

Desde PowerShell, en esta carpeta:

```powershell
$env:JAVA_HOME = "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --console=plain
```

El APK se genera en `app\build\outputs\apk\debug\app-debug.apk`.
Las pruebas de contrato Android usan MockWebServer. Las pruebas de integración
del backend usan SQLite temporal; no crean cuentas en Neon.
