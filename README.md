# Proyecto Computación Paralela y Distribuida - Steam

Este repositorio contiene todo el código fuente del proyecto, el cual es una aplicación distribuida en Java que actúa como herramienta de análisis y comparación de precios de juegos usando la API oficial de Steam. Implementa funcionalidades como comparación de precios regionales y búsqueda de biblioteca compartida (Family Sharing).

**Nota importante:** Todo el código del proyecto se encuentra alojado en este repositorio de GitHub, junto con una carpeta que contiene los archivos de la base de datos (`.sql`) necesarios para su funcionamiento.

---

## 🛠️ Requisitos Previos

Para ejecutar este proyecto y alojar su base de datos localmente, necesitarás tener instalado **XAMPP** u otro entorno similar (WAMP, MAMP, etc).

### 1. Instalación de XAMPP

1. Dirígete a la página oficial de [Apache Friends](https://www.apachefriends.org/es/index.html) y descarga la versión de XAMPP correspondiente a tu sistema operativo (Windows, Linux o macOS).
2. Ejecuta el instalador descargado y sigue las instrucciones del asistente de instalación. 
   - *Nota:* Es imprescindible que instales los componentes **MySQL** y **phpMyAdmin**, ya que son necesarios para montar la base de datos.
3. Una vez instalado, abre el **Panel de Control de XAMPP**.
4. Inicia los módulos de **Apache** y **MySQL** haciendo clic en los botones **"Start"** correspondientes.

### 2. Integración de la Base de Datos

Para integrar la base de datos adjunta en el repositorio a tu entorno local, sigue estos pasos:

1. Asegúrate de que los módulos de Apache y MySQL estén en ejecución (con fondo verde) en tu Panel de Control de XAMPP.
2. Abre tu navegador web y dirígete a la siguiente dirección: [http://localhost/phpmyadmin/](http://localhost/phpmyadmin/).
3. En el panel izquierdo de phpMyAdmin, haz clic en **"Nueva"** para crear una nueva base de datos.
4. Ingresa el nombre de la base de datos "project_db_extended" (o cualquier nombre, ya que el script SQL la creará automáticamente). Luego haz clic en **"Crear"**.
5. Selecciona la base de datos recién creada haciendo clic sobre ella en el panel izquierdo.
6. En el menú superior de opciones, haz clic en la pestaña **"Importar"**.
7. En la sección "Archivo a importar", haz clic en el botón **"Seleccionar archivo"** (o "Choose File").
8. Navega hasta la carpeta del proyecto que clonaste de GitHub, busca la carpeta `BD`, correspondiente a la base de datos y selecciona el archivo `project_db.sql` que contiene la estructura y los datos.
9. Desplázate hasta el final de la página y haz clic en el botón **"Importar"** (o "Go") para ejecutar el script. Si todo sale bien, verás un mensaje de éxito y las tablas aparecerán en tu base de datos.

---

### 3. Configurar la API Key de Steam

El servidor necesita una Steam Web API Key para consultar las bibliotecas de los perfiles de usuarios (funcionalidad Family Sharing). Se recomienda configurarla como variable de entorno para no exponerla en el código fuente:

**Windows (PowerShell — sesión actual):**
```powershell
$env:STEAM_API_KEY = "tu_api_key_aqui"
```

**Windows (permanente, para el usuario actual):**
```powershell
[System.Environment]::SetEnvironmentVariable("STEAM_API_KEY", "tu_api_key_aqui", "User")
```

**Linux / macOS:**
```bash
export STEAM_API_KEY="tu_api_key_aqui"
```

Puedes obtener tu propia API Key en: https://steamcommunity.com/dev/apikey

---

## 🚀 Ejecución del Proyecto (Entrega 2 - Clúster Distribuido)

1. Clona este repositorio en tu máquina local (si no lo has hecho aún).
2. Configura la variable de entorno `STEAM_API_KEY` (ver sección anterior).
3. Asegúrate de tener compilado el proyecto.
4. **Levantar el Clúster (3 Nodos):**
   Para ejecutar el entorno distribuido, debes levantar tres instancias del servidor. Puedes hacerlo desde tu IDE ejecutando `RunServer.java` tres veces (pasándole como argumento el ID `1`, `2` y `3`), o bien usando los scripts proporcionados en la raíz del proyecto. Abre 3 terminales distintas y ejecuta:
   - Terminal 1: `./run_nodo1.sh` (o `.bat` en Windows)
   - Terminal 2: `./run_nodo2.sh` (o `.bat` en Windows)
   - Terminal 3: `./run_nodo3.sh` (o `.bat` en Windows)
5. **Ejecutar Cliente Regular:**
   Ejecuta `RunClient.java` para interactuar de forma manual con el sistema.
6. **Ejecutar Prueba de Carga:**
   Ejecuta `RunCarga.java` (o el script `./run_carga.sh`) para simular múltiples usuarios concurrentes y probar la exclusión mutua. Durante esta prueba puedes detener manualmente uno de los nodos para observar el algoritmo de recuperación en vivo.
