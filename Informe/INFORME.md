

1.  Introducción
El presente proyecto corresponde al desarrollo de un sistema distribuido en Java inspirado en funcionalidades de la plataforma Steam. El sistema permite realizar consultas de juegos, búsqueda de precios regionales y operaciones críticas de compra y recarga de saldo, integrando mecanismos de comunicación distribuida, coordinación entre nodos, ordenamiento lógico de eventos y tolerancia básica a fallos.
La solución fue diseñada para ejecutarse sobre tres o más nodos independientes, cada uno levantado como un proceso/JVM separado. Cada nodo atiende clientes externos mediante sockets TCP y, al mismo tiempo, se comunica con sus pares usando un canal peer-to-peer dedicado para algoritmos de coordinación y detección de fallos. Sobre esta base se implementaron relojes de Lamport, elección de coordinador mediante Bully, exclusión mutua distribuida con Ricart-Agrawala y detección de fallos mediante heartbeats.
El objetivo del proyecto no fue únicamente construir un programa concurrente, sino demostrar en la práctica varios de los conceptos fundamentales de la computación paralela y distribuida: ausencia de reloj global, necesidad de orden lógico, coordinación entre procesos distribuidos, manejo de fallos y evaluación del comportamiento bajo carga concurrente.
2. Objetivos 

2.2 Objetivo general 
Desarrollar un sistema distribuido en Java, ejecutado sobre múltiples nodos, capaz de atender consultas y operaciones críticas mediante comunicación por sockets, coordinación distribuida y tolerancia básica a fallos.

2.3 Objetivos específicos
Implementar una topología real de tres nodos o más, ejecutados como procesos independientes.
Permitir comunicación distribuida entre nodos usando sockets TCP y estructuras de datos serializadas.
Incorporar relojes lógicos de Lamport para ordenar eventos relevantes del sistema.
Implementar un algoritmo de elección de coordinador y un algoritmo de exclusión mutua distribuida.
Detectar caídas de nodos mediante heartbeats y timeouts.
Ejecutar pruebas de carga concurrente y recopilar métricas como throughput, latencia promedio, latencia percentil 95 y mensajes de coordinación.







3. Descripción General Del Sistema
El sistema implementado corresponde a una arquitectura distribuida multiservidor con coordinación Peer-to-Peer entre nodos. Cada nodo ejecuta la lógica principal del sistema, atiende solicitudes de clientes externos y participa en los mecanismos de coordinación distribuida junto con los demás nodos del clúster.
La aplicación está inspirada en funcionalidades de la plataforma Steam y permite realizar operaciones como consulta de juegos, comparación de precios regionales, recarga de saldo y compra de juegos. Dentro de estas funcionalidades, la compra de juegos constituye una operación crítica, ya que modifica recursos compartidos como el saldo del usuario, su biblioteca de juegos y los registros de compra. Por esta razón, dicha operación requiere mecanismos de exclusión mutua distribuida para evitar condiciones de carrera e inconsistencias cuando varias solicitudes son procesadas concurrentemente por distintos nodos.
Los nodos se configuran mediante un archivo estático nodes.txt, donde se define para cada uno su identificador, host, puerto de atención a clientes y puerto de comunicación Peer-to-Peer. Esta información es cargada por el módulo RegistroNodos, el cual actúa como mecanismo de membresía estática dentro del sistema.
Cada nodo se levanta a través de la clase node.NodoPeer, la cual integra los principales componentes de la arquitectura. El componente ClientHandler se encarga de recibir y atender las solicitudes externas mediante sockets TCP. El componente ServerImpl contiene la lógica de negocio del sistema, procesando operaciones como la consulta de juegos, la comparación de precios, la recarga de saldo y la compra de juegos. Además, el componente EscuchadorNodos permite recibir mensajes distribuidos provenientes de otros nodos del clúster.
Para la coordinación distribuida, el sistema incorpora distintos mecanismos. RelojLamport permite mantener un orden lógico de los eventos distribuidos; RicartAgrawala se utiliza para controlar el acceso exclusivo a la sección crítica durante operaciones como la compra de juegos; EleccionBully permite elegir un coordinador ante escenarios de falla; GestorLatidos supervisa la disponibilidad de los nodos mediante heartbeats; y ConsensoBizantino permite acordar valores globales, como una promoción activa de la tienda, mediante un esquema simplificado de consenso.
De esta forma, el sistema combina atención concurrente de clientes, procesamiento distribuido, coordinación entre nodos, tolerancia básica a fallos y persistencia centralizada mediante una base de datos MySQL compartida. Esta organización permite demostrar conceptos fundamentales de la computación distribuida, tales como ausencia de reloj global, ordenamiento lógico de eventos, exclusión mutua distribuida, elección de coordinador, detección de fallos y consistencia en operaciones críticas.

4.  Fundamentación y Teoría
4.1.  Concurrencia y ausencia de reloj global
 4.1.1. Concurrencia en el sistema
La concurrencia se manifiesta de dos formas en el proyecto: 
Primero, a nivel de cliente, donde múltiples usuarios pueden hacer peticiones simultáneas que son tratadas por hilos dedicados en los nodos, usando server.ClientHandler.
Segundo, a nivel de clúster, donde los tres nodos operan de forma concurrente y paralela, comunicándose entre sí para mantener la consistencia del sistema, detectar fallos y coordinar el acceso a recursos.
  4.1.2. Ausencia de reloj global
La falta de un reloj global en sistemas distribuidos es una característica principal de estos. 
En este proyecto cada nodo (node.NodoPeer) ejecuta su propio proceso JVM independiente escuchando en puertos distintos (5000 a 5002 para clientes externos y 6000 a 6002 para comunicación P2P inter-nodo). Esta separación de los procesos garantiza que ningún nodo asuma que su tiempo local coincide con el de los otros, lo que hace imprescindible el uso de un algoritmo de ordenamiento de eventos.

4.2.  Justificación de uso de Lamport o Reloj Vectorial
 4.2.1. Por qué usamos Lamport
En este proyecto se optó por utilizar relojes de Lamport debido a que permiten ordenar lógicamente los eventos distribuidos de manera simple, eficiente y suficiente para los objetivos del sistema. Como la aplicación está compuesta por múltiples nodos ejecutándose como procesos independientes, no existe un reloj global confiable que permita establecer el orden real de los eventos solo a partir del tiempo físico. Frente a este problema, el reloj de Lamport entrega una forma práctica de asociar una marca lógica a cada evento de envío y recepción de mensajes. 

Por ejemplo, al recibir un mensaje P2P de otro nodo, el servidor sincroniza su reloj local de acuerdo a la regla de Lamport (tiempo = max(local, recibido) + 1):

long lamportRecibido = msg.obtenerTiempoLamport();
nodo.getReloj().actualizar(lamportRecibido);


Esta decisión fue adecuada para el proyecto, ya que los algoritmos implementados, como Ricart-Agrawala y la coordinación entre nodos, requieren comparar el orden de solicitudes y respuestas distribuidas, pero no necesariamente mantener el historial causal completo que entregan los relojes vectoriales. Por ello, Lamport permitió reducir la complejidad de implementación y, al mismo tiempo, mantener trazabilidad y verificabilidad en los logs del sistema.
  
 4.2.2. Problema que resuelve
El uso de Lamport resuelve el problema de ordenar eventos en un entorno distribuido donde distintos nodos pueden ejecutar acciones concurrentes sin un reloj compartido. En particular, permite determinar un orden lógico entre solicitudes de acceso a la sección crítica, respuestas entre nodos y mensajes de coordinación, evitando ambigüedades sobre cuál evento ocurrió antes desde el punto de vista del sistema distribuido. Esto es especialmente importante en la exclusión mutua distribuida, donde varias solicitudes pueden generarse casi al mismo tiempo y se necesita un criterio consistente para decidir cuál debe ser atendida primero. Además, Lamport facilita demostrar en los registros del sistema el orden en que ocurrieron eventos relevantes, lo que resulta útil tanto para depuración como para justificar el comportamiento del sistema durante la presentación.
 
4.3.  Justificación de transparencia
4.3.1. Transparencia de Acceso: 

La transparencia de acceso se logra porque los clientes interactúan con el sistema mediante un mismo protocolo de comunicación basado en sockets TCP y objetos serializados Request y Response, sin importar qué nodo del clúster atienda la solicitud. 

Desde la perspectiva del cliente, la conexión y el intercambio de mensajes se abstraen mediante flujos de objetos serializados (ObjectOutputStream y ObjectInputStream):

socket = new Socket(host, puerto);
out    = new ObjectOutputStream(socket.getOutputStream());
in     = new ObjectInputStream(socket.getInputStream());


out.writeObject(new Request(TipoRequest.COMPRAR_JUEGO, parametros));
Response res = (Response) in.readObject();


De esta forma, la invocación a operaciones como búsqueda de juegos, consulta de precios regionales o compra de juegos se mantiene uniforme, ya que todas siguen la misma estructura de petición y respuesta. Esto permite que el acceso a las funciones del sistema sea consistente y homogéneo, ocultando parcialmente la complejidad interna de la lógica distribuida y de los algoritmos de coordinación implementados entre nodos.

4.3.2. Transparencia de Ubicación: 

La transparencia de ubicación se cumple de manera parcial, ya que las funciones del sistema pueden ser atendidas por cualquiera de los nodos disponibles del clúster y el generador de carga tiene capacidad de reenviar solicitudes a otro nodo si uno deja de responder. Esto permite que el servicio continúe operando aun cuando cambie el nodo que efectivamente procesa una petición. Sin embargo, la ubicación no está completamente abstraída, porque el cliente todavía conoce explícitamente el host y el puerto del nodo al que se conecta inicialmente. Por lo tanto, el sistema sí presenta una forma básica de transparencia de ubicación, pero no una transparencia total como la que existiría con un balanceador, descubrimiento dinámico o middleware especializado.

5.  Modelado de Ingeniería
5.1.  Modelo Físico
 El modelo describe toda nuestra arquitectura de hardware y red, donde se despliega y se ejecuta la aplicación, indicando cómo se comunican los dispositivos entre sí con el servidor.

A continuación, se presenta el diagrama físico de red que modela los flujos de comunicación descritos, ilustrando los protocolos, puertos de interconexión y límites de la red local frente a la red externa.


![Diagrama Físico del Sistema Distribuido](Informe/Images/modelo_fisico.png)


5.1.1 Identificación de Nodos
Nodos Cliente (Cliente A, B y Generador de Carga): Representan los dispositivos finales que ejecutan la aplicación cliente. Su función es proporcionar la interfaz al usuario y capturar sus peticiones, adicionalmente se incluye el generador de carga, un proceso independiente que puede simular varios clientes concurrentes para poner a prueba el sistema

Nodos Peer (Servidores locales): A diferencia de una arquitectura centralizada clásica, el sistema despliega múltiples máquinas (o instancias de JVM) que ejecutan la clase node.NodoPeer. Estos nodos actúan como pares, distribuyendo la carga de los clientes y coordinándose entre sí. Adicionalmente, todo los nodos acceden de manera centralizada al motor MySQL local, asumiendo así el rol de persistencia compartida.

Nodo Externo (API Steam): Corresponde a la infraestructura de servidores web proporcionada por la plataforma Steam (Valve), la cual actúa como proveedor de datos externos en tiempo real (precios, perfiles, catálogos).

5.1.2 Descripción del Entorno de Red
 El sistema hace un uso de múltiples protocolos y redes para así poder garantizar su funcionamiento, en el cuál se divide en tres niveles de interacción:

Entorno Cliente-Servidor (Red TCP/IP): Los Nodos Cliente se conectan al Nodo Servidor a través de una red TCP/IP, la cuál puede operar dentro de un entorno LAN o a través de la WAN. Esta comunicación se realiza utilizando Sockets, apuntando específicamente al puerto “5000, 5001 y 5002”, en donde por este canal fluyen tanto las peticiones como las respuestas.

Entorno Servidor-Exterior (Red WAN / Internet): Para así poder obtener los datos en tiempo real, los Nodos Peer deben tener salida a internet(Wan). Cada nodo se comporta de manera independiente como un cliente web que realiza peticiones a la api de steam, asi utilizando los protocolos HTTP (Puerto 80) y HTTPS (Puerto 443)

Entorno Interno (Localhost / LAN): Dentro del entorno de Servidores Locales, los nodos conforman una malla peer-to-peer utilizando sockets TCP (Puertos 6000, 6001, 6002) para coordinar los algoritmos distribuidos, a su vez la comunicación entre cada nodo y la BD ocurre de manera interna dentro de la misma máquina física (LocalHost), utilizando el protocolo JDBC sobre TCP/IP  a traves del puerto estandar 3306 de MySQL



 5.2.  Modelo Arquitectonico
El sistema adopta una arquitectura distribuida híbrida, basada en múltiples nodos servidores que operan como pares dentro de una red Peer-to-Peer. Cada nodo se encuentra organizado en tres capas principales: una capa de atención a clientes, una capa de lógica de negocio y una capa de coordinación distribuida.

La capa de atención a clientes está compuesta por el componente ClientHandler, encargado de recibir y gestionar las solicitudes externas provenientes de clientes o del generador de carga mediante conexiones TCP. Esta capa representa el punto de entrada al sistema y permite que cualquier nodo del clúster pueda atender peticiones de forma concurrente.

La capa de lógica de negocio, implementada mediante ServerImpl, procesa las operaciones principales del sistema, tales como la consulta de juegos, la comparación de precios regionales, la recarga de saldo y la compra de juegos. En particular, la compra de juegos corresponde a una operación crítica, ya que modifica información compartida como el saldo del usuario, su biblioteca de juegos y los registros de transacciones en la base de datos. Por esta razón, esta capa actúa como intermediaria entre las solicitudes recibidas, la persistencia de datos y los mecanismos de coordinación distribuida.

Por otra parte, la capa de coordinación distribuida permite que los nodos se comuniquen entre sí mediante enlaces P2P, con el objetivo de coordinar sus acciones dentro del sistema. En esta capa se integran algoritmos como Lamport, Ricart-Agrawala, Bully, Consenso Bizantino y Heartbeats. Lamport permite ordenar lógicamente los eventos distribuidos; Ricart-Agrawala controla el acceso exclusivo a la sección crítica durante operaciones como la compra de juegos; Bully permite elegir un coordinador frente a escenarios de falla; el Consenso Bizantino simplificado permite acordar valores globales como promociones de la tienda; y Heartbeats permite detectar la caída o recuperación de nodos.

Finalmente, todos los nodos acceden a una base de datos MySQL compartida, lo que centraliza la persistencia de la información. De esta manera, la arquitectura combina comunicación cliente-servidor para la recepción de solicitudes, comunicación Peer-to-Peer para la coordinación distribuida y una capa de persistencia centralizada para el almacenamiento de datos. Esta organización permite que el sistema atienda múltiples clientes de manera concurrente, mantenga consistencia en operaciones críticas y tolere fallos básicos dentro del clúster.

![Diagrama Arquitectónico del Sistema Distribuido](Informe/Images/modelo_arqui.png)

5.2.1 Capa de atención a clientes
La capa de atención a clientes corresponde al punto de entrada de las solicitudes externas al sistema. En esta capa se encuentra el componente ClientHandler, encargado de recibir las conexiones provenientes de clientes externos o del generador de carga mediante comunicación TCP.

Su principal responsabilidad es aceptar las peticiones entrantes, interpretar la solicitud recibida y derivarla hacia la capa de lógica de negocio para su procesamiento. De esta forma, esta capa actúa como intermediaria entre los usuarios o procesos externos y el funcionamiento interno de cada nodo del sistema.

Además, al estar presente en cada NodoPeer, permite que cualquier nodo pueda recibir solicitudes, evitando que el sistema dependa exclusivamente de un único punto de entrada. Esto favorece una distribución más flexible de la carga y permite que el sistema mantenga un comportamiento distribuido desde la recepción de las peticiones.

5.2.2 Capa de lógica de negocio  
La capa de lógica de negocio se encuentra representada por el componente ServerImpl. Esta capa es responsable de procesar las operaciones principales del sistema, aplicando las reglas necesarias para responder correctamente a cada solicitud recibida desde la capa de atención a clientes.

En esta capa se concentra la lógica funcional del sistema, incluyendo operaciones como consulta de juegos, comparación de precios regionales, recarga de saldo y compra de juegos. Esta última es especialmente relevante dentro del modelo arquitectónico, ya que corresponde a una transacción crítica que modifica recursos compartidos, como el saldo del usuario, su biblioteca de juegos y el historial de compras almacenado en la base de datos.

Cuando una solicitud corresponde a una operación de lectura o consulta, la capa de lógica de negocio puede procesarla directamente o apoyarse en servicios externos, como la API de Steam. Sin embargo, cuando la solicitud implica una modificación crítica del estado del sistema, como la compra de un juego, ServerImpl debe coordinarse previamente con la capa de coordinación distribuida antes de acceder a la base de datos. Esto permite evitar condiciones de carrera, sobregiros de saldo o inconsistencias entre compras concurrentes atendidas por distintos nodos.

Por esta razón, ServerImpl actúa como un componente central dentro de cada NodoPeer, conectando la atención de clientes, la coordinación distribuida y la persistencia de datos.


5.2.3 Capa de coordinación distribuida.
La capa de coordinación distribuida permite que los nodos del sistema se comuniquen entre sí mediante enlaces P2P para coordinar sus acciones. Esta capa es fundamental dentro de la arquitectura, ya que permite que los distintos NodoPeer trabajen de manera conjunta sin depender completamente de un coordinador fijo o de un servidor central para la toma de decisiones.

Dentro de esta capa se implementan distintos algoritmos distribuidos. El algoritmo de Lamport permite establecer un orden lógico entre los eventos generados por los nodos, lo que resulta necesario en un sistema donde no existe un reloj global compartido. Ricart-Agrawala se utiliza para controlar el acceso a la sección crítica, especialmente durante la operación de compra de juegos, evitando que dos nodos modifiquen simultáneamente recursos compartidos como el saldo o la biblioteca de un usuario. Por su parte, el algoritmo Bully permite realizar la elección de un coordinador cuando sea necesario, especialmente frente a escenarios de falla. El algoritmo de Consenso Bizantino simplificado permite acordar de forma replicada valores de configuración global, como una promoción activa en la tienda. Finalmente, los Heartbeats permiten detectar si un nodo sigue activo mediante el envío periódico de señales entre los participantes del sistema.

Gracias a esta capa, el sistema puede mantener coordinación entre los nodos, detectar fallos, ordenar eventos y controlar el acceso a recursos compartidos. Esto refuerza el carácter distribuido de la arquitectura y permite que los nodos actúen como pares dentro de la red.





5.2.4 Flujo General del Sistema
El flujo general del sistema comienza cuando un cliente externo o generador de carga envía una solicitud mediante una conexión TCP hacia uno de los nodos disponibles. Esta solicitud es recibida por la capa de atención a clientes, específicamente por el componente ClientHandler, el cual se encarga de gestionar la conexión y derivar la petición hacia la lógica interna del nodo.

Posteriormente, la solicitud es procesada por la capa de lógica de negocio, implementada en ServerImpl. En esta etapa se determina qué operación debe ejecutarse y si esta requiere acceder a la base de datos compartida o coordinarse previamente con otros nodos. Si la operación no requiere coordinación especial, el nodo puede procesarla directamente y consultar o modificar la información almacenada en MySQL.

En caso de que la solicitud involucre recursos compartidos, sincronización o decisiones distribuidas, la capa de lógica de negocio se comunica con la capa de coordinación distribuida. En esta fase, los nodos intercambian mensajes P2P utilizando los algoritmos correspondientes, como Lamport, Ricart-Agrawala, Bully o Heartbeats, dependiendo del objetivo de la operación.

Una vez finalizada la coordinación, el nodo ejecuta la acción correspondiente y, si es necesario, accede a la base de datos MySQL compartida para consultar o persistir información. Finalmente, el resultado de la operación es devuelto al cliente a través de la conexión TCP inicial.

En términos generales, el flujo puede resumirse de la siguiente manera:

Cliente externo / Generador de carga
                   ↓ (TCP)
Capa de atención a clientes
        ↓
Capa de lógica de negocio
        ↓
Capa de coordinación distribuida, si corresponde
        ↓
Base de datos MySQL compartida
        ↓
Respuesta al cliente



Este flujo permite que el sistema combine una entrada de solicitudes de tipo cliente-servidor, una lógica de procesamiento local en cada nodo, una coordinación Peer-to-Peer entre nodos y una persistencia centralizada mediante una base de datos compartida.

 
5.3.  Modelado de Funciones
Respecto al comportamiento dinámico del sistema lo analizaremos a través de los dos modelos de función usando diagramas de secuencia UML que ilustran la interacción y el flujo de mensajes entre los distintos componentes distribuidos (Cliente, Servidor y APIs externas). 

5.3.1 Diagrama de secuencia para “Compra de Juegos”
Esta función crítica ilustra la aplicación práctica del ordenamiento causal y la necesidad de exclusión mutua en sistemas distribuidos. Dado que múltiples nodos pueden recibir solicitudes de compra de un mismo usuario simultáneamente, el sistema debe garantizar que el saldo en la billetera virtual no se gaste de manera concurrente (evitando condiciones de carrera y sobregiros).

Para lograrlo, cuando el ClientHandler recibe una petición de COMPRAR_JUEGO, el servidor adjunta el valor actual del Reloj de Lamport y solicita acceso a la Sección Crítica mediante el algoritmo de Ricart-Agrawala. El nodo transmite a todos sus pares un mensaje RA_PETICION y entra en estado de espera. Una vez que todos los demás nodos evalúan la marca de tiempo lógica (cediendo el turno al de menor timestamp) y responden con RA_RESPUESTA, el nodo original obtiene el acceso exclusivo. En ese momento, ejecuta la transacción de lectura y escritura contra la Base de Datos MySQL, descuenta el saldo, asigna el juego, y finalmente libera el recurso enviando el respectivo aviso a los demás nodos, manteniendo así una estricta coherencia e integridad transaccional que obedece al orden de eventos de Lamport

![Diagrama de Secuencia: "Compra de Juegos (Transacción con Exclusión Mutua)"](Informe/Images/compra_juego.png)

5.3.2 Diagrama de secuencia para “Comparar precios en múltiples países”
Esta función tiene como objetivo obtener el precio de un juego específico en múltiples países de forma simultánea. El cliente envía una solicitud al servidor a través del ClientHandler, que la delega al ServerImpl. Este crea un pool de hasta 20 hilos paralelos, asignando un hilo por cada país solicitado. Cada hilo consulta de forma independiente la Steam API para obtener el precio del juego en la moneda local del país correspondiente, y luego convierte ese valor a USD usando el método convertirPrecioAUSD. Una vez que todos los hilos han completado su consulta, el servidor recopila los resultados en una lista y la retorna al cliente.
La concurrencia en esta función es importante para su eficiencia, ya que en lugar de consultar los países uno por uno de forma secuencial, todas las solicitudes a la Steam API se realizan en paralelo, reduciendo significativamente el tiempo total de respuesta. Es relevante cuando se consultan muchos países a la vez, ya que el cuello de botella principal es la latencia de red hacia la API externa.


![Diagrama de Secuencia: "Comparar precios en múltiples países"](Informe/Images/comparar_paises.png)

6.  Análisis fundamental



6.1.  Modelo de seguridad
El sistema distribuido usa sockets TCP estándar sin cifrado. Los objetos Java se serializan en formato binario sin envoltura TLS/SSL, lo que expone el canal a interceptaciones. 

6.1.1 Identificación de Canal de Inseguridad
Canal de Cliente hacia NodoPeer: es un canal TCP puro, donde los objetos Request/Response viajan sin cifrar, acá un atacante en la red LAN podría interceptar o inyectar mensajes.

Canal de NodoPeer hacia NodoPeer: es un canal P2P TCP puro, donde los mensajes también se transmiten como Java serializado. Este canal se encarga de coordinar los algoritmos de exclusión mutua y elección de coordinador. El fallo en este canal podría alterar quién accede a la sección crítica. 

Canal de NodoPeer hacia Base de datos MySQL: Esta conexión se construye en ServerImpl con credenciales hardcodeadas como valores por defecto. Si el puerto usado no está restringido por un firewall, cualquier proceso en la red puede conectarse directamente a la base de datos.

Canal de HTTP hacia la API Steam Web: En este canal la consulta de bibliotecas de usuarios utiliza http:// en vez de usar https://, exponiendo la Steam API Key en tránsito

6.1.2 Descripción de Amenazas y formas de mitigación
Debido a la naturaleza de los canales identificados, el sistema está expuesto principalmente a las siguientes vulnerabilidades:

Intercepción de credenciales/ Ataque Man in the Middle: Un atacante en la misma red puede capturar un Request con un comando INICIAR_SESION y extraer el nombre de usuario y la contraseña en plainText. 

Robo de contraseñas de usuario: Las contraseñas están cifradas en MD5, el problema es que este no es resistente a ataques de fuerza bruta con GPU. Un atacante que obtenga la tabla usuarios puede revertir los hashes MD5 en minutos con herramientas como Hashcat.

Filtrado de API Key de Steam: La Steam API Key hardcodeada en el código fuente permite consultar las bibliotecas privadas de cualquier cuenta de Steam. Si el repositorio se hace público, la key puede ser extraída y usada para abusar de la cuota de la API o acceder a datos privados de los usuarios.


Deserialización insegura: Ambos canales usan ObjectInputStream para deserializar objetos. Un atacante que construya un payload malicioso puede ejecutar código arbitrario en el proceso JVM del servidor.

Inyección SQL: Un atacante malicioso puede conectarse a uno de los puertos Peer to Peer y enviar un mensaje a los nodos con el tipo COORDINADOR y un ID arbitrario. Esto haría que todos los nodos se actualizarán a un nodo coordinador inexistente, bloqueando la coordinación distribuida. También podría enviar mensajes tipo RA_RESPUESTA falsos para forzar a un nodo a entrar a la sección crítica sin la autorización de sus pares

Acceso directo a la base de datos: Si el puerto 3306 no está restringido por firewall, un atacante puede conectarse directamente a MySQL usando las credenciales por defecto (root sin contraseña). Tendría acceso total a todas las tablas: usuarios, billeteras, compras, bibliotecas

Ataques DDoS: El servidor acepta conexiones ilimitadas en el puerto cliente. Cada conexión lanza un hilo (ClientHandler). Un atacante puede abrir miles de conexiones simultáneas, agotando los recursos de la JVM (OutOfMemoryError).

	
6.1.3 Propuestas de Mitigación Técnicas
Para robustecer el sistema y cerrar las brechas de seguridad mencionadas, se proponen las siguientes implementaciones técnicas nativas en Java:

Intercepción de credenciales/ Ataque Man in the Middle: Para mitigar esta falla se pueden envolver los sockets en SSLSocket. En Java esto se puede implementar con SSLContext + SSLSocketFactory en cliente y SSLServerSocket en servidor

Robo de contraseñas de usuario: Migrar a bcrypt con factor de coste ≥ 12

Filtrado de API Key de Steam: Eliminar el fallback hardcodeado; fallar explícitamente si la variable de entorno no está definida

Deserialización insegura: Implementar ObjectInputFilter para crear una whitelist de clases permitidas

Inyección SQL: Autenticar cada MensajeNodo con HMAC-SHA256 usando una clave compartida entre nodos

Acceso directo a la base de datos: Leer credenciales exclusivamente desde variables de entorno; nunca usar valores hardcodeados

Ataques DDoS: Limitar conexiones concurrentes con ThreadPoolExecutor con tamaño fijo
6.2.  Modelo de fallos
 6.2.1 Clasificación de fallos esperados 
     En el sistema implementado se identificaron los tres tipos principales de fallos:

Crash: El fallo por crash ocurre al momento que un proceso JVM se detiene abruptamente, al detenerse el nodo deja de responder ambos puertos, cliente y peer. Este fallo es el más frecuente en el sistema y este está diseñado para tolerarlo.

Evidencia en logs: En nodo1.log, cuando Nodo-1 inicia antes que los otros nodos se registra:
[BULLY][Nodo-1] No se pudo contactar a Nodo-2@localhost (peer=6001): Connection refused 
[BULLY][Nodo-1] No se pudo contactar a Nodo-3@localhost (peer=6002): Connection refused
Para el Nodo-1 esto es un crash de los otros nodos.

Evidencia en el log de carga: En load_test_20260604_232421.log, al finalizar la prueba el Nodo-3 aparece como NO DISPONIBLE (Nodo caído), confirmando que este se detuvo durante la prueba.
		
Omisión de mensajes o respuestas: El fallo por omisión ocurre cuando un mensaje enviado no llega a su destino o la respuesta no llega al emisor, esto puede ocurrir por un timeout de red, descarte de paquetes o sobrecarga del receptor.

Omisión de envío: GestorLatidos.enviarLatidos() captura las excepciones de red al intentar enviar LATIDO:
} catch (Exception e)  { // Ignorar: el supervisor de pares detectará la caída }
		Si un latido no llega por congestión de red, el monitor lo detectará en la siguiente ventana de evaluación

Omisión de recepción: GeneradorCarga.enviarPeticion() tiene un timeout de 5 segundos (socket.setSoTimeout(5000)). Si el servidor no responde en ese tiempo, la petición se registra como fallida y se intenta con otro nodo

 Omisión de respuesta: Si un RA_RESPUESTA se pierde en tránsito, Ricart-Agrawala quedaría bloqueado indefinidamente esperando el semáforo. La mitigación es el mecanismo alDetectarFalloDePar, que libera el semáforo cuando el heartbeat confirma que el nodo cayó
	

Falla Bizantina (Comportamiento Arbitrario): Los fallos bizantinos ocurren cuando un nodo activo se comporta de manera arbitraria o maliciosa. El sistema actual no posee una forma de tolerancia a los fallos bizantinos. 
No hay una validación criptográfica de la identidad del emisor de un MensajeNodo
No hay mecanismos de votación que detecten si un nodo envía información errónea
No hay checksums sobre el contenido de los mensajes
Esto es aceptable para el contexto del proyecto, pero un sistema en producción real requeriría algoritmos como PBFT(Practical Byzantine Fault Tolerance) para manejar estos fallos.

6.2.2 Estrategias de detección
     Para evitar que el sistema se quede estancado en un bloqueo de tiempo indefinido, se implementaron los siguientes mecanismos de detección de errores:

Detección por Heartbeat: Esta es la estrategia principal de detección, funciona con dos tareas programadas en un ScheduledExecutorService de 2 hilos:

Tarea Emisora: Este hilo envía un MensajeNodo de tipo LATIDO a todos los pares cada 2 segundos (INTERVALO_LATIDO_SEG = 2). Cada latido lleva la marca de tiempo Lamport actual del nodo emisor
programador.scheduleAtFixedRate(this::enviarLatidos, 1, 2, TimeUnit.SECONDS);

Tarea Monitora: Este hilo compara el timestamp real del último latido recibido de cada par con el instante actual. Si la diferencia supera el tiempo límite asignado (TIEMPO_LIMITE_SEG = 6) de 6 segundos, osea, 3 latidos consecutivos perdidos, el nodo se declara como caido
programador.scheduleAtFixedRate(this::supervisarNodos, 2, 2, TimeUnit.SECONDS);

Detección de Timeout de Socket: En las conexiones P2P que se generen en enviarAPar(), existe un timeout de conexión de 1500ms o 1.5s. Si la conexión falla, el error se registra pero no se declara el nodo como caído, ese es el trabajo del heartbeat. Esta separación evita falsos positivos por congestión momentánea.
s.connect(new InetSocketAddress(host, puerto), 1500);

Detección implícita del algoritmo Ricart-Agrawala:  Si un nodo en espera de RA_RESPUESTA recibe la notificación de fallo de ese par via alDetectarFallosDePar(), libera el semáforo automáticamente.
public void alDetectarFalloDePar(int idNodoFallido) {
    nodosFallidos.add(idNodoFallido);
    boolean esperado;
    synchronized (lockEstado) {
        if (deseandoSC) {
            esperado = nodosEsperados.remove(idNodoFallido);
        }
    }
    if (esperado) {
        semaforoRespuestas.release();
    }
}
Esto garantiza que un fallo de un par durante una operación no bloquee indefinidamente al nodo solicitante

 6.2.3 Recuperación programada
     Una vez que se haya detectado el fallo, el sistema ejecuta rutinas para mantener la integridad de los datos y evitar el colapso del sistema:

Recuperación de Coordinador – Algoritmo Bully: Al detectar que el coordinador cayó, el GestorLatidos inicia bully.iniciarEleccion() en un hilo separado:
El nodo iniciador envía ELECCION a todos los nodos con una ID mayor
Si alguno responde con OK en 3000 ms se espera que ese nodo se enuncie como COORDINADOR. Si no lo anuncia en 6000 ms, se reinicia la elección
Si nadie responde en 3000 ms, el iniciador se declara coordinador y envía COORDINADOR a todos sus pares.
	El coordinador se declara con el mayor ID posible, garantizando unicidad.
Esta rutina de recuperación se puede evidenciar en los logs, como por ejemplo en      nodo1.log, al inicio se registra el proceso completo
[BULLY][Nodo-1] Nuevo coordinador: Nodo-3 (lamport=19)
[HB][Nodo-1] RECUPERACION: Nodo-3 volvió a responder (lamport=25)
[RA][Nodo-1] Par Nodo-3 se ha recuperado, removiendo de lista de caídos

Recuperación tras Exclusión mutua: Cuando un nodo cae mientras otro espera su RA_RESPUESTA, el alDetectarFalloDePar actúa como un "voto fantasma": libera el semáforo como si el nodo caído hubiera respondido afirmativamente. Esto permite que el solicitante entre a la sección crítica con los nodos restantes.
Cuando el nodo se recupera, alRecuperarPar lo elimina de nodosFallidos para que vuelva a participar en futuras rondas de exclusión mutua
public void alRecuperarPar(int idNodoRecuperado) {
    nodosFallidos.remove(idNodoRecuperado);
}

Recuperación de Base de datos: Los procesos comprarJuego y recargarSaldo usan transacciones con setAutoCommit(false) y bloqueo a nivel de fila (FOR UPDATE). Si ocurre cualquier error durante la transacción (fallo de red, violación de restricción), se ejecuta connection.rollback(). 
} catch (SQLException e) {
    connection.rollback();
    throw e;
} finally {
    connection.setAutoCommit(true);
}
Esto garantiza que la base de datos nunca quede en un estado inconsistente





Recuperación de Cliente: Si un nodo no responde, el generador de carga itera sobre todos los nodos configurados en nodes.txt hasta encontrar uno disponible
for (int retry = 0; retry < maxRetries; retry++) {
    int index = (currentServerIndex.get() + retry) % maxRetries;
    // Intentar con nodosConfig.get(index)
}
Esto permite que la prueba de carga continúe funcionando incluso cuando uno de los nodos es terminado durante la ejecución

   6.2.4 Matriz de Resumen de Fallos y Recuperación

Con el objetivo de sintetizar el análisis fundamental del sistema, a continuación se presenta     una matriz integrada que vincula las amenazas de seguridad y los tipos de fallos distribuidos (Crash y Omisión) con sus respectivos protocolos de detección y recuperación. Esta tabla permite visualizar cómo las decisiones de diseño técnico y la implementación de herramientas nativas de Java (como el manejo de excepciones, la sincronización de hilos y la gestión de transacciones JDBC) actúan como mecanismos de mitigación para reducir la superficie de ataque y garantizar la resiliencia de la arquitectura frente a condiciones adversas en entornos de red reales.

| Categoría | Posible Fallo / Amenaza | Protocolo de Recuperación / Solución implementada en Java | Superficie de Ataque Mitigada |
|-----------|-------------------------|-----------------------------------------------------------|-------------------------------|
| Conexiones a APIs Externas (Steam) | Caída de la API de Steam, Timeouts o respuestas HTTP de error (Omission / Crash). | Manejo de Excepciones HTTP | Denegación de servicio (DDoS) indirecta |
| Concurrencia y Multihilos | Fallo en un hilo específico al procesar múltiples países o perfiles simultáneamente. | Aislamiento de tareas con CompletableFuture | Resource Exhaustion (Agotamiento de recursos) |
| Sincronización de Memoria (Estado Local) | Condición de carrera al leer o modificar el listado de juegos/países simultáneamente por varios clientes. | Exclusión Mutua (synchronized) | Ataques de Concurrencia |
| Consistencia de Base de Datos | Error de escritura o desconexión abrupta durante la persistencia de datos (Crash / Omission). | Gestión manual de transacciones (Rollback) | Corrupción de Integridad de Datos |
| Comunicación Sockets (Cliente-Servidor) | Desconexión abrupta del cliente (crash) o envío de objetos corruptos por el socket (fallo bizantino). | Manejo seguro de I/O en ClientHandler | Memory Leaks y Conexiones Huérfanas |
| Inyección de Código | Inserción de comandos maliciosos mediante los campos de texto del cliente | Uso de Consultas Parametrizadas | Inyección SQL |
| Elección de Coordinador (Algoritmo Bully) | Caída del nodo coordinador mientras los demás nodos operan (Crash) | GestorLatidos detecta la ausencia tras 6 segundos sin un heartbeat y lanza una elección de coordinador | Punto único de fallo en la coordinación |
| Exclusión Mutua (Algoritmo de Ricart-Agrawala) | Caída de un nodo mientras otro espera su RA_RESPUESTA para entrar a la sección crítica (Omisión) | La función alDetectarFalloDePar() libera el semáforo contando al nodo caído como voto implícito. | Deadlock distribuido |
| Fallos Bizantinos | Un nodo envía un valor malicioso o corrupto como propuesta de promoción global | ConsensoBizantino.alRecibirPropuesta() valida que el valor no sea nulo ni vacío antes de emitir un voto. | Replicación inconsistente de estado global |

Figura 8: Matriz de resumen de Fallos y Recuperación


6.3 Lectura de la prueba de tráfico
La prueba de carga real simuló el comportamiento concurrente de 50 usuarios accediendo al sistema de manera simultánea, solicitando las funciones de lectura sin bloqueo (consulta de juegos) y funciones con exclusión mutua distribuida (compras).

Throughput: El sistema fue capaz de atender de manera sostenida la prueba concurrente con un throughput de 85.86 peticiones por segundo, logrando procesar 3994 peticiones exitosas en 69.56 segundos, gracias a la topología distribuida de ClientHandler y a los pools asincrónicos para la consulta a la API de Steam.

Latencia Promedio y P95: La latencia mínima fue cercana a 0 ms, mientras que la latencia p95 reflejó un valor muy eficiente de 40.72 ms. Esto indica que el 95% de las operaciones se completaron de forma muy controlada y sin bloqueos extendidos, confirmando que el mayor costo temporal está dominado por las consultas externas a Steam y no por el overhead local.

Mensajes de Coordinación: La exclusión mutua distribuida generó un intenso tráfico P2P, registrándose 5388 mensajes de coordinación en total (como peticiones y respuestas RA). Este volumen es congruente con la fórmula proporcional de 2 × (N - 1) mensajes por transacción crítica bajo el algoritmo de Ricart-Agrawala. 

Recuperación en Falla Inducida: Tras apagar el Nodo-3 de manera deliberada (marcado como “NO DISPONIBLE” en la tabla), el sistema reconoció el fallo pasados los latidos máximos configurados (6 segundos). Al detectar la caída, el mecanismo alDetectarFalloDePar liberó los bloqueos del nodo inactivo, permitiendo al sistema continuar operando. La tasa de error del 33.13% se explica por las peticiones que estaban siendo atendidas por dicho nodo al momento de su caída, o aquellas que superaron su timeout durante los segundos de detección, demostrando una recuperación exitosa.


![Tabla de Reporte Prueba de Carga](/Informe/Images/tabla_prueba_carga.png)

![Tabla de Métricas de Coordinación distribuida (Ricart-Agrawala)](/Informe/Images/tabla_ricart_walala.png)


7.  Conclusión
Por último, en el desarrollo de este proyecto se ha demostrado la viabilidad y eficiencia de implementar un sistema distribuido bajo una arquitectura de tres capas para la gestión masiva de datos provenientes de plataformas externas. La integración de un modelo de concurrencia avanzado, que combina la asignación de un hilo por cliente con la ejecución asincrónica mediante un pool de hilos, resultó ser una solución altamente efectiva. Esta estrategia permitió paralelizar las consultas a la API de Steam, reduciendo los tiempos de respuesta al resolver operaciones de alto costo computacional, como la comparativa internacional de precios, sin comprometer el rendimiento general del servidor principal. 
Respecto de la tolerancia a fallos, la arquitectura diseñada exhibe un alto grado de resiliencia frente a los desafíos inherentes de las redes distribuidas. Al asumir la ausencia de un reloj global y la imprevisibilidad de las conexiones de red, el sistema logró mitigar con éxito las fallas de tipo crash y omisión. La implementación de rutinas de recuperación programadas, como el aislamiento de hilos defectuosos para entregar respuestas parciales, el manejo transaccional de la base de datos para evitar la corrupción de información y la limpieza proactiva de recursos ante desconexiones abruptas, garantiza que el servicio mantenga su disponibilidad e integridad incluso cuando los nodos externos o internos experimentan caídas.  
Finalmente, el análisis fundamental de la plataforma subraya la importancia de evolucionar el diseño hacia un modelo de comunicación mucho más estricto. Si bien el sistema cumple a cabalidad con los requerimientos de funcionalidad, concurrencia y transparencia, el diagnóstico de seguridad revela vulnerabilidades críticas en la capa de transporte, particularmente la exposición a ataques de interceptación y la inyección de código por deserialización insegura debido al uso de canales sin encriptar. Por consiguiente, la adopción de las mitigaciones propuestas, tales como la implementación de túneles SSL/TLS, el uso forzado del protocolo HTTPS y la validación estricta de objetos entrantes, se perfila como el paso definitivo para consolidar este proyecto como una herramienta de software no solo robusta y eficiente, sino también íntegramente segura.


Anexos
Anexo 1: Diagrama de Entidad-Relación de la base de datos

![Diagrama de Entidad-Relación: "Base de datos Steam"](Informe/Images/diagrama_bd.png)

