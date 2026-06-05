# Informe Técnico: Arquitectura Distribuida para el Comparador de Precios Steam
**Asignatura:** Programación Concurrente y Distribuida  
**Formato:** Pregrado de la Escuela de Ingeniería  

---

## 1. Fundamentación y Teoría (Sección 4.1)

### 1.1 El Problema del Reloj Global en Sistemas Distribuidos
En un sistema centralizado clásico, el orden de los eventos se determina fácilmente mediante el reloj físico del sistema operativo. Sin embargo, en nuestro sistema distribuido de comparación de precios de Steam (compuesto por 3 nodos independientes ejecutándose en JVMs separadas), confiar en los relojes físicos es imposible debido al **desvío de reloj (clock drift)** provocado por variaciones térmicas, latencia de red y limitaciones de hardware.

Sin una sincronización física perfecta (inviable a gran escala), no podemos determinar con precisión qué transacción ocurrió antes. Por ejemplo:
*   Un usuario **recarga saldo** en el `Nodo 1` a las `19:50:00.001` (hora física local de Nodo 1).
*   Otro usuario realiza la **compra de un juego** en el `Nodo 2` a las `19:49:59.999` (hora física local de Nodo 2).
*   Debido al desvío del reloj físico, podría parecer que la compra ocurrió *antes* de la recarga de saldo, cuando en realidad la recarga ocurrió primero. Si el balance del usuario dependía de esa recarga, el sistema podría rechazar erróneamente la compra por "saldo insuficiente".

### 1.2 Justificación de los Relojes Lógicos de Lamport
Para resolver el ordenamiento causal sin depender de relojes físicos, implementamos los **Relojes Lógicos de Lamport** en nuestra clase `RelojLamport.java`. Este mecanismo define una relación de orden parcial ("sucedió-antes" o $\rightarrow$) basada en tres reglas de consistencia:
1.  **Eventos Locales:** Si los eventos $a$ y $b$ ocurren dentro del mismo nodo, y $a$ ocurre antes que $b$, entonces $L(a) < L(b)$. El reloj local se incrementa en $1$ con cada evento (`tick()`).
2.  **Transmisión de Mensajes:** Si el evento $a$ es el envío de un mensaje por un nodo, y el evento $b$ es la recepción de ese mensaje por otro nodo, entonces $L(a) < L(b)$.
3.  **Actualización del Receptor:** Al recibir un mensaje con una marca de tiempo $L_{msg}$, el nodo receptor actualiza su reloj lógico local: 
    $$L_{local} = \max(L_{local}, L_{msg}) + 1$$

#### ¿Por qué Lamport y no Relojes Vectoriales?
*   **Relación de Orden Total:** Para la consistencia de la base de datos de juegos y saldos, necesitamos un **orden total** de eventos (no solo parcial). Lamport nos permite romper empates de manera consistente utilizando el identificador único de cada nodo: $(L_a, id_a) < (L_b, id_b)$ si $L_a < L_b$, o si $L_a = L_b$ e $id_a < id_b$.
*   **Eficiencia de Red:** Los relojes vectoriales requieren transmitir un vector de tamaño $N$ (donde $N$ es el número de nodos) en cada mensaje. Para nuestra arquitectura de $3$ nodos, esto es manejable, pero para escalabilidad futura añade overhead innecesario. Como no necesitamos detectar concurrencia exacta de manera estricta (es decir, identificar si dos eventos son causalmente independientes), el reloj escalar de Lamport (`RelojLamport`) es la solución óptima y más ligera.

### 1.3 Transparencia de Acceso y Ubicación
El sistema garantiza **Transparencia de Acceso** y **Transparencia de Ubicación**:
*   **Transparencia de Acceso:** Los clientes externos (`Client.java`) interactúan con los nodos distribuidos utilizando exactamente la misma interfaz y protocolo de sockets que utilizaban con el servidor centralizado. El cliente no percibe que por debajo hay algoritmos de exclusión mutua o relojes lógicos coordinando la base de datos.
*   **Transparencia de Ubicación:** El cliente se conecta a cualquiera de los nodos disponibles (puertos `5000`, `5001` o `5002`) de manera indistinta. Todas las consultas de búsqueda de juegos y comparación regional se responden con el mismo formato. Si un nodo cae, el cliente simplemente se conecta a otro nodo disponible en el cluster, manteniendo el servicio activo.
*   **Transparencia de Ubicación de Persistencia:** Para el despliegue multinodo en máquinas físicas reales de la red local, la ubicación de la base de datos MySQL no es rígida. Mediante variables de entorno en [ServerImpl.java](file:///Users/dazinha/Desktop/ICI%20PUCV/9%C2%B0%20Semestre%20ICI%20PUCV/Computacion%20Paralela/Proyecto-Paralela-Steam-Entrega-2-main/src/server/ServerImpl.java), los nodos resuelven dinámicamente la dirección del host MySQL compartido (`DB_HOST`), permitiendo descentralizar los nodos de cómputo y centralizar la persistencia sin forzar bases de datos locales inconsistentes.

### 1.4 Dos Funciones Principales y Justificación de su Distribución
El sistema distribuido está estructurado en base a dos funciones primordiales del negocio, cuya descentralización responde a necesidades claras de rendimiento, consistencia y disponibilidad:

1.  **Consulta y Comparación Regional de Precios (Operación de Lectura):**
    *   *Descripción:* Permite buscar títulos del catálogo y consultar los precios de los juegos en diferentes monedas locales y su conversión a dólares de forma concurrente desde la API de Steam.
    *   *Justificación de Distribución:* Al ser lecturas puras que representan el 90% del tráfico, distribuirlas permite la escalabilidad horizontal lineal. Las consultas se pueden procesar en cualquier réplica de forma totalmente paralela y asíncrona, descongestionando los nodos transaccionales y mejorando sustancialmente el rendimiento global del sistema sin requerir bloqueos de red costosos.
2.  **Transacciones de Compra y Recarga de Billetera (Operación de Escritura / Recurso Crítico):**
    *   *Descripción:* Permite recargar saldo de forma atómica en la cuenta de un usuario y realizar la compra de un videojuego deduciendo saldo de su balance virtual y registrándolo en su biblioteca personal.
    *   *Justificación de Distribución:* Al modificar el estado y saldo del usuario (recurso crítico), es imperativo que las escrituras se coordinen para evitar condiciones de carrera (como dobles compras con saldo insuficiente). Distribuir esta lógica con Ricart-Agrawala y ordenarla mediante relojes de Lamport garantiza que el estado sea consistente en todo el cluster de manera descentralizada, eliminando el riesgo de que la caída de un servidor central detenga el flujo transaccional.

---

## 2. Modelado de Ingeniería (Sección 4.2)

### 2.1 Diagrama de Modelo Físico
Representa la topología de red, los puertos asignados y la conexión a la base de datos central.

```mermaid
graph TD
    subgraph Clientes Externos
        C1[Cliente 1 - Java Client]
        C2[Cliente 2 - Generador de Carga]
    end

    subgraph Cluster Distribuido Steam
        N1[Nodo 1<br/>ID: 1<br/>puertoCliente: 5000<br/>puertoPeer: 6000]
        N2[Nodo 2<br/>ID: 2<br/>puertoCliente: 5001<br/>puertoPeer: 6001]
        N3[Nodo 3 - Coordinador<br/>ID: 3<br/>puertoCliente: 5002<br/>puertoPeer: 6002]
    end

    DB[(MySQL Database<br/>project_db_extended<br/>Port: 3306)]

    %% Conexiones de Clientes a Puertos de Cliente
    C1 -.->|Socket TCP| N1
    C2 -.->|50 Threads TCP| N2

    %% Conexiones P2P Bidireccionales
    N1 <===>|TCP Sockets / Mensajes Latido| N2
    N2 <===>|TCP Sockets / Mensajes Latido| N3
    N3 <===>|TCP Sockets / Mensajes Latido| N1

    %% Conexiones a Base de Datos
    N1 ===>|JDBC Link| DB
    N2 ===>|JDBC Link| DB
    N3 ===>|JDBC Link| DB
```

### 2.2 Diagrama del Modelo Arquitectónico
Muestra la organización en capas lógicas y cómo cooperan los diferentes módulos en español.

```mermaid
graph TD
    subgraph Capa de Presentación
        Client[Client.java / GeneradorCarga.java]
    end

    subgraph Capa de Coordinación Distribuida
        PL[EscuchadorNodos.java<br/>Socket Server P2P]
        LC[RelojLamport.java<br/>Reloj Lógico Escalar]
        BE[EleccionBully.java<br/>Algoritmo de Elección]
        RA[RicartAgrawala.java<br/>Exclusión Mutua]
        HM[GestorLatidos.java<br/>Detección de Fallas]
    end

    subgraph Capa de Lógica de Negocio
        CH[ClientHandler.java<br/>Atención de Peticiones]
        SI[ServerImpl.java<br/>Controlador de Reglas]
    end

    subgraph Capa de Persistencia
        DB_Connector[MySQL JDBC Connector]
        MySQL[(Base de Datos MySQL)]
    end

    %% Flujos de interacción
    Client ==>|Request / Lamport Time| CH
    CH ==>|1. Request Access| RA
    RA -.->|Intercambio RA_SOLICITUD/RA_RESPUESTA P2P| PL
    PL -.->|Update Clock| LC
    RA ==>|2. Execute Operation| SI
    SI ==>|SQL Queries| DB_Connector
    DB_Connector ==> MySQL
    HM -.->|Latidos periódicos| PL
    PL -.->|Falla detectada| BE
```

### 2.3 Diagramas UML de Secuencia con Relojes de Lamport

#### A) Búsqueda y Comparación Regional de Precios (Lectura sin bloqueo)
Este diagrama muestra una consulta de lectura distribuida típica con el paso de relojes lógicos.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant Nodo1 as Nodo 1 (Local)
    participant DB as MySQL DB

    Note over Cliente, Nodo1: Reloj Cliente = 0, Reloj Nodo 1 = 10
    
    Cliente->>Nodo1: Request(GET_PRECIOS_REGIONALES, juegoId, Lamport = 0)
    Note over Nodo1: Al recibir: Update Clock<br/>max(10, 0) + 1 = 11
    
    Nodo1->>DB: SELECT * FROM precios_regionales WHERE juego_id = ...
    DB-->>Nodo1: ResultSet (Datos de precios)
    
    Note over Nodo1: Tick local antes de enviar<br/>11 + 1 = 12
    Nodo1->>Cliente: Response(Precios, Lamport = 12)
    
    Note over Cliente: Al recibir: Update Clock<br/>max(0, 12) + 1 = 13
```

#### B) Compra de Juego con Ricart-Agrawala (Escritura Exclusiva Distribuida)
Muestra cómo el `Nodo 1` adquiere el recurso crítico contactando al `Nodo 2` y `Nodo 3`.

```mermaid
sequenceDiagram
    autonumber
    actor Cliente
    participant N1 as Nodo 1 (Solicitante)
    participant N2 as Nodo 2 (Par)
    participant N3 as Nodo 3 (Par)
    participant DB as MySQL DB

    Note over N1, N3: Estado: Idle. Relojes: N1=20, N2=15, N3=18

    Cliente->>N1: Request(COMPRAR_JUEGO, juegoId, Lamport = 0)
    Note over N1: ClientHandler invoca ra.requestAccess()<br/>Estado -> Requesting. Tick Reloj N1: 20 + 1 = 21

    N1->>N2: MensajeNodo(RA_SOLICITUD, Lamport = 21)
    N1->>N3: MensajeNodo(RA_SOLICITUD, Lamport = 21)

    Note over N2: Recibe SOLICITUD de N1 (L=21)<br/>Update N2 Clock: max(15, 21)+1=22<br/>N2 está IDLE -> concede acceso de inmediato
    Note over N3: Recibe SOLICITUD de N1 (L=21)<br/>Update N3 Clock: max(18, 21)+1=22<br/>N3 está IDLE -> concede acceso de inmediato

    N2-->>N1: MensajeNodo(RA_RESPUESTA, Lamport = 22)
    N3-->>N1: MensajeNodo(RA_RESPUESTA, Lamport = 22)

    Note over N1: Recibe RESPUESTAS de todos los nodos activos.<br/>Update N1 Clock: max(21, 22)+1=23<br/>Estado -> Held (Adquiere Sección Crítica)

    N1->>DB: UPDATE usuarios SET saldo = saldo - precio ... INSERT INTO biblioteca ...
    Note over N1: Operación completada con éxito.

    Note over N1: ClientHandler invoca ra.releaseAccess()<br/>Estado -> Idle. Tick Reloj N1: 23 + 1 = 24
    N1->>Cliente: Response(Compra Exitosa, Lamport = 24)
```

#### C) Caída del Coordinador y Elección Bully
Muestra qué ocurre cuando el `Nodo 3` (coordinador) se apaga, y el `Nodo 1` detecta la falla por falta de latidos.

```mermaid
sequenceDiagram
    autonumber
    participant N1 as Nodo 1 (ID: 1)
    participant N2 as Nodo 2 (ID: 2)
    participant N3 as Nodo 3 (ID: 3 - Caído)

    Note over N1, N2: GestorLatidos monitoriza latidos periódicos
    Note over N3: N3 sufre un fallo crítico y se apaga (Crash-Stop)
    Note over N1: Han pasado 6 segundos sin latidos de N3.<br/>¡N3 detectado como caído!
    Note over N1: Dispara elección Bully. Tick local N1 = 50

    N1->>N2: MensajeNodo(ELECCION, Lamport = 50)
    
    Note over N2: Recibe ELECCION de N1 (ID=1).<br/>Como N2 tiene ID mayor (2 > 1), responde OK
    N2-->>N1: MensajeNodo(OK, Lamport = 51)
    
    Note over N2: N2 toma el control de la elección y envía a nodos con ID superior (N3)
    N2->>N3: MensajeNodo(ELECCION, Lamport = 52)
    Note over N2: Espera 3 segundos (timeout de respuesta)
    Note over N2: N3 no responde porque está caído.
    
    Note over N2: N2 se proclama nuevo coordinador.<br/>Tick local N2 = 53
    N2->>N1: MensajeNodo(COORDINADOR, Lamport = 53)
    
    Note over N1: Recibe COORDINADOR de N2.<br/>Registra a Nodo 2 como nuevo líder.
```

---

## 3. Modelo de Fallos y Seguridad (Sección 4.3)

### 3.1 Tabla de Modelo de Fallos

| Componente | Tipo de Falla | Método de Detección | Estrategia de Recuperación |
| :--- | :--- | :--- | :--- |
| **Nodo Coordinador** | Crash-Stop (Apagado / Caída física) | Los nodos esclavos dejan de recibir mensajes `LATIDO` por más de 6 segundos en `GestorLatidos`. | El primer nodo en detectarlo inicia una ronda del algoritmo **Bully** (`EleccionBully`). El nodo activo con el ID más alto se auto-proclama nuevo coordinador. |
| **Nodos Esclavos** | Crash-Stop | El coordinador y los demás pares detectan la ausencia de `LATIDO`. | Se marcan temporalmente como inactivos en el `RegistroNodos`. Ricart-Agrawala se adapta automáticamente, ya no esperando sus respuestas `RA_RESPUESTA` para la sección crítica. |
| **Red / Enlaces** | Omisión de Mensajes / Pérdida | Conexión fallida o tiempo de espera agotado (timeout de 1500 ms en sockets P2P y 1000 ms en heartbeats). | Se detecta la falla de red y se descarta el mensaje. El sistema se apoya en el `GestorLatidos` para marcar el nodo como caído tras 6 segundos de inactividad, lo que reorganiza dinámicamente el conjunto de respuestas esperadas en Ricart-Agrawala. |
| **Base de Datos MySQL** | Crash / Desconexión | Excepciones JDBC (`SQLException`) capturadas en `ServerImpl`. | Los nodos capturan el error, devuelven un mensaje de error limpio al cliente (`Response` con éxito = false) y reintentan la conexión con un pool de conexiones interno. |

### 3.2 Análisis de Canales Expuestos y Amenazas de Seguridad
Nuestra arquitectura distribuida prioriza el rendimiento y la consistencia lógica. No obstante, al abrir sockets TCP planos para la comunicación inter-nodo (`puertoPeer` 6000-6002), se exponen varias vulnerabilidades que deben documentarse:

1.  **Canal Inter-Nodo Pleno (Sin Cifrado):**
    *   *Amenaza:* Los mensajes `MensajeNodo` (incluidos `RA_SOLICITUD`, `RA_RESPUESTA` y latidos) se serializan y envían en texto claro por sockets TCP. Un atacante en la misma red local podría interceptar el tráfico mediante *sniffing* de red.
    *   *Mitigación Académica:* Implementar **SSL/TLS (SSLSocket)** para cifrar el canal de comunicación P2P entre nodos y autenticar a los pares mediante certificados digitales válidos.
2.  **Falta de Autenticación de Membresía:**
    *   *Amenaza:* Cualquier proceso malicioso que se conecte al puerto de inter-nodo (ej. `6001`) y envíe un flujo constante de mensajes `ELECCION` o `COORDINADOR` falsificados puede sabotear la coordinación y forzar elecciones infinitas (ataque de denegación de servicio o *hijacking* de coordinador).
    *   *Mitigación Académica:* Exigir un protocolo de saludo *(handshake)* criptográfico basado en tokens o firma digital al establecer una conexión inter-nodo antes de procesar cualquier mensaje P2P.

---

## 4. Análisis e Interpretación de Resultados (Sección 3)

### 4.1 Métricas de Rendimiento Reales (Prueba de Carga de 60 Segundos)
Sometimos el clúster a una prueba de carga con **50 hilos concurrentes** durante **60 segundos sostenidos** (duración real registrada de 69.56 segundos) a través de `GeneradorCarga`. Durante la ejecución se alternaron consultas de lectura (`BUSCAR_JUEGO` y `GET_PRECIOS_REGIONALES`) con operaciones de exclusión mutua transaccional (`COMPRAR_JUEGO`).

> [!NOTE]
> **Consistencia en Datos de Prueba:** Los identificadores de juegos simulados en [GeneradorCarga.java](file:///Users/dazinha/Desktop/ICI%20PUCV/9%C2%B0%20Semestre%20ICI%20PUCV/Computacion%20Paralela/Proyecto-Paralela-Steam-Entrega-2-main/src/load/GeneradorCarga.java) se alinean estrictamente con los cargados en base de datos por [project_db.sql](file:///Users/dazinha/Desktop/ICI%20PUCV/9%C2%B0%20Semestre%20ICI%20PUCV/Computacion%20Paralela/Proyecto-Paralela-Steam-Entrega-2-main/BD/project_db.sql). Esto garantiza que la tasa de error reflejada a continuación represente puramente las restricciones lógicas del negocio (saldo insuficiente, juegos duplicados en la biblioteca) y no inconsistencias en las claves de acceso de la persistencia relacional.

A continuación se presenta la tabla resumen de las métricas de rendimiento recolectadas en el log de la prueba:

| Métrica | Valor Obtenido | Descripción |
| :--- | :---: | :--- |
| **Duración Total** | 69.56 s | Tiempo total de ejecución sostenido. |
| **Peticiones Totales** | 5,973 | Suma global de peticiones enviadas al clúster. |
| **Peticiones Exitosas** | 3,994 | Peticiones procesadas con éxito por las réplicas. |
| **Peticiones Fallidas** | 1,979 | Intentos fallidos (ej. saldo insuficiente o juego ya poseído por el usuario). |
| **Tasa de Error Global** | 33.13% | Porcentaje de operaciones fallidas a nivel lógico de negocio. |
| **Throughput Promedio** | **85.86 req/s** | Cantidad de peticiones atendidas por segundo por el clúster. |
| **Latencia Mínima** | 0 ms | Tiempo de respuesta mínimo registrado en consultas locales en caché. |
| **Latencia Promedio** | **40.72 ms** | Tiempo medio de respuesta a través de todo el tráfico. |
| **Latencia P95** | **69 ms** | Percentil 95 de tiempo de respuesta (el 95% tardó menos de 69 ms). |
| **Latencia Máxima** | 1,315 ms | Latencia máxima registrada durante el pico de caída y re-organización. |

### 4.2 Cantidad de Mensajes del Algoritmo de Coordinación
El algoritmo de exclusión mutua distribuida de **Ricart-Agrawala** generó un intercambio coordinado de mensajes P2P (`RA_SOLICITUD` y `RA_RESPUESTA`) para proteger las operaciones críticas de base de datos. Los contadores registrados por nodo al término de la prueba fueron:

*   **Nodo-1 (`localhost:5000`):** 2,952 mensajes de coordinación.
*   **Nodo-2 (`localhost:5001`):** 2,436 mensajes de coordinación.
*   **Nodo-3 (`localhost:5002`):** *NO DISPONIBLE* (Nodo derribado a los ~25 segundos).
*   **Total de Mensajes P2P Generados:** **5,388 mensajes**.

### 4.3 Comportamiento ante Falla Inducida y Recuperación
A los **25 segundos** de iniciada la prueba de carga, se indujo un fallo físico deteniendo el proceso del **Nodo-3 (Coordinador)**. 

1.  **Detección:** El `GestorLatidos` en los nodos esclavos (Nodo 1 y Nodo 2) detectó la pérdida de latidos tras 6 segundos de inactividad.
2.  **Reorganización y Re-elección (Bully):** El Nodo-2 inició la ronda de elección Bully y se proclamó nuevo coordinador, notificando al Nodo-1.
3.  **Adaptación de Exclusión Mutua (Ricart-Agrawala):** Al ser notificado de la caída, `RicartAgrawala` de forma automática adaptó su lista de miembros esperados, ignorando al Nodo-3 y permitiendo que la sección crítica continuara operando con el quórum de nodos activos.
4.  **Impacto en Rendimiento:** Durante la re-elección de Bully, la latencia máxima experimentó un pico temporal de **1,315 ms**. Sin embargo, gracias al mecanismo de *failover* activo en el cliente y al ajuste del quórum, no hubo pérdida de peticiones por caídas de red y el throughput se estabilizó de nuevo a ~85 req/s.

### 4.4 Gráfico de Rendimiento (Latencia vs Throughput)
El siguiente gráfico ilustra la evolución temporal de la latencia y el throughput durante la prueba de carga, destacando la inyección de la falla y la recuperación automática del sistema distribuido:

![Gráfico de Métricas de Carga y Falla Inducida](logs/load_test_metrics.png)

---

## 5. Logs de Corrida y Evidencias Adjuntas
Las trazas completas con marcas de tiempo lógico de Lamport, elecciones de Bully, exclusiones de Ricart-Agrawala y registros de latencia se encuentran adjuntas en el directorio de evidencias de la entrega:
*   **Log del Generador de Carga:** [load_test_20260604_232421.log](logs/load_test_20260604_232421.log)
*   **Log del Nodo 1 (Slave/Pares):** [nodo1.log](logs/nodo1.log)
*   **Log del Nodo 2 (Pares/Nuevo Coordinador):** [nodo2.log](logs/nodo2.log)
*   **Log del Nodo 3 (Coordinador Inicial Caído):** [nodo3.log](logs/nodo3.log)

