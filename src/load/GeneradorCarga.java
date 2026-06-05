package load;

import common.Juego;
import common.Pais;
import common.PrecioRegional;
import common.Request;
import common.Response;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generador de carga concurrente para la prueba de estrés del sistema distribuido Steam.
 *
 * Cumple los requisitos de la Sección 3 de la pauta:
 *  - Al menos 50 hilos (clientes) simultáneos
 *  - Duración mínima de 60 segundos sostenidos
 *  - Ejercita las 2 funciones principales:
 *      1. Búsqueda de juegos (BUSCAR_JUEGO)
 *      2. Precios regionales (GET_PRECIOS_REGIONALES)
 *  - Ejercita el recurso protegido por RA: COMPRAR_JUEGO
 */
public class GeneradorCarga {

    private final String host;
    private final int puerto;
    private final int cantidadHilos;
    private final long duracionMs;
    private final ColectorMetricas metricas;
    private final java.util.List<node.InfoNodo> nodosConfig;
    private final java.util.concurrent.atomic.AtomicInteger currentServerIndex = new java.util.concurrent.atomic.AtomicInteger(0);
    
    // Reloj de Lamport local silencioso para evitar inundar la consola con miles de logs
    private final node.RelojLamport relojLocal = new node.RelojLamport(888, true);

    // IDs de juegos populares de Steam para usar en las pruebas (alineados con la BD para evitar violaciones de clave foránea)
    private static final int[] GAME_IDS = {730, 550, 271590, 1091500, 319510, 1245620};

    // Países para consultar precios regionales
    private static final String[] CODIGOS_PAISES = {"US", "CL", "AR", "BR", "MX", "ES"};

    private static final Random random = new Random();

    // Este metodo tiene como objetivo inicializar el generador con la configuracion de la prueba
    public GeneradorCarga(String host, int puerto, int cantidadHilos, long duracionSegundos) {
        this.host          = host;
        this.puerto        = puerto;
        this.cantidadHilos = cantidadHilos;
        this.duracionMs    = duracionSegundos * 1000L;
        this.metricas      = new ColectorMetricas();
        
        // Cargar lista de nodos para Failover
        node.RegistroNodos registro = new node.RegistroNodos("nodes.txt");
        this.nodosConfig = registro.obtenerTodosLosNodos();
        
        // Encontrar índice del servidor inicial en la lista
        for (int i = 0; i < nodosConfig.size(); i++) {
            node.InfoNodo n = nodosConfig.get(i);
            if (n.obtenerHost().equalsIgnoreCase(host) && n.obtenerPuertoCliente() == puerto) {
                this.currentServerIndex.set(i);
                break;
            }
        }
    }

    /**
     * Ejecuta la prueba de carga.
     * Lanza cantidadHilos hilos concurrentes que envían peticiones durante duracionMs.
     */
    public void ejecutar() {
        System.out.println("═".repeat(60));
        System.out.println("  INICIANDO PRUEBA DE CARGA");
        System.out.printf("  Host: %s:%d | Hilos: %d | Duración: %d s%n",
                host, puerto, cantidadHilos, duracionMs / 1000);
        System.out.println("═".repeat(60));

        ExecutorService ejecutor = Executors.newFixedThreadPool(cantidadHilos);
        CountDownLatch barreraInicio = new CountDownLatch(1);
        AtomicBoolean enEjecucion = new AtomicBoolean(true);

        // Lanzar todos los hilos de carga
        for (int i = 0; i < cantidadHilos; i++) {
            final int idHilo = i;
            ejecutor.submit(() -> {
                try {
                    barreraInicio.await(); // Esperar la señal de inicio
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                cicloClienteHilo(idHilo, enEjecucion);
            });
        }

        // Señal de inicio: todos los hilos comienzan al mismo tiempo
        barreraInicio.countDown();
        System.out.println("[GeneradorCarga] Todos los " + cantidadHilos + " hilos iniciados.");

        // Esperar la duración de la prueba
        try {
            Thread.sleep(duracionMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Detener todos los hilos
        enEjecucion.set(false);
        ejecutor.shutdown();
        try {
            ejecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        // Mostrar reporte final
        metricas.imprimirReporte();
        
        // Consultar métricas de coordinación distribuida de todos los nodos
        System.out.println("─".repeat(60));
        System.out.println("  MÉTRICAS DE COORDINACIÓN DISTRIBUIDA (RICART-AGRAWALA)");
        System.out.println("─".repeat(60));
        long totalMensajesCoordinacion = 0;
        try {
            node.RegistroNodos registro = new node.RegistroNodos("nodes.txt");
            for (node.InfoNodo n : registro.obtenerTodosLosNodos()) {
                try (Socket s = new Socket(n.obtenerHost(), n.obtenerPuertoCliente());
                     ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(s.getInputStream())) {
                    
                    s.setSoTimeout(2000);
                    out.writeObject(new Request(Request.Command.OBTENER_METRICAS_COORDINACION));
                    out.flush();
                    
                    Response res = (Response) in.readObject();
                    if (res.isSuccess()) {
                        long count = (Long) res.getResult();
                        totalMensajesCoordinacion += count;
                        System.out.printf("  [Coordinación] Nodo-%d (%s:%d): %d mensajes (Req/Rep SC)%n",
                                n.obtenerIdNodo(), n.obtenerHost(), n.obtenerPuertoCliente(), count);
                    }
                } catch (Exception e) {
                    System.out.printf("  [Coordinación] Nodo-%d (%s:%d): NO DISPONIBLE (Nodo caído)%n",
                            n.obtenerIdNodo(), n.obtenerHost(), n.obtenerPuertoCliente());
                }
            }
            System.out.println("─".repeat(60));
            System.out.printf("  Total Mensajes de Coordinación (Ricart-Agrawala): %d%n", totalMensajesCoordinacion);
            System.out.println("═".repeat(60));
        } catch (Exception e) {
            System.out.println("  [Error] No se pudo leer la configuración de nodos para cargar métricas: " + e.getMessage());
        }
    }

    // Este metodo tiene como objetivo ejecutar el loop de peticiones de un hilo cliente durante la prueba
    private void cicloClienteHilo(int idHilo, AtomicBoolean enEjecucion) {
        int contadorPeticiones = 0;
        while (enEjecucion.get() && !Thread.currentThread().isInterrupted()) {
            // Alternar entre los 3 tipos de operaciones para ejercitar todo el sistema
            int tipoOp = contadorPeticiones % 3;
            try {
                switch (tipoOp) {
                    case 0: realizarBuscarJuego(); break;
                    case 1: realizarPreciosRegionales(); break;
                    case 2: realizarComprarJuego(idHilo); break;
                }
            } catch (Exception e) {
                // Error ya registrado dentro de cada método
            }
            contadorPeticiones++;

            // Pequeña pausa aleatoria para simular comportamiento real de usuarios
            try {
                Thread.sleep(random.nextInt(50));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // Este metodo tiene como objetivo enviar una peticion BUSCAR_JUEGO y registrar su latencia
    private void realizarBuscarJuego() {
        int gameId = GAME_IDS[random.nextInt(GAME_IDS.length)];
        String query = String.valueOf(gameId);
        enviarPeticion(new Request(Request.Command.BUSCAR_JUEGO, query));
    }

    // Este metodo tiene como objetivo enviar una peticion GET_PRECIOS_REGIONALES y registrar su latencia
    private void realizarPreciosRegionales() {
        int gameId = GAME_IDS[random.nextInt(GAME_IDS.length)];
        ArrayList<Pais> paises = new ArrayList<>();
        // Seleccionar 3 países aleatorios
        for (int i = 0; i < 3; i++) {
            String cc = CODIGOS_PAISES[random.nextInt(CODIGOS_PAISES.length)];
            paises.add(new Pais(cc, cc));
        }
        enviarPeticion(new Request(Request.Command.GET_PRECIOS_REGIONALES, gameId, paises));
    }

    // Este metodo tiene como objetivo enviar una peticion COMPRAR_JUEGO (recurso critico con RA)
    private void realizarComprarJuego(int idHilo) {
        // Usar userId diferente por hilo para evitar conflictos reales de BD
        int userId  = (idHilo % 5) + 1; // IDs 1-5
        int gameId  = GAME_IDS[random.nextInt(GAME_IDS.length)];
        double precio = 9.99;
        enviarPeticion(new Request(Request.Command.COMPRAR_JUEGO, userId, gameId, precio));
    }

    // Este metodo tiene como objetivo conectar al nodo, enviar una peticion y registrar la latencia con tolerancia a fallos
    private void enviarPeticion(Request req) {
        long inicio = System.currentTimeMillis();
        int maxRetries = nodosConfig.isEmpty() ? 1 : nodosConfig.size();
        
        for (int retry = 0; retry < maxRetries; retry++) {
            int index = (currentServerIndex.get() + retry) % maxRetries;
            String targetHost = host;
            int targetPort = puerto;
            if (!nodosConfig.isEmpty()) {
                node.InfoNodo targetNode = nodosConfig.get(index);
                targetHost = targetNode.obtenerHost();
                targetPort = targetNode.obtenerPuertoCliente();
            }
            
            try (Socket socket = new Socket()) {
                socket.connect(new java.net.InetSocketAddress(targetHost, targetPort), 1500);
                socket.setSoTimeout(5000); // Timeout de 5 segundos para lectura
                
                try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {
                    
                    // Asignar el tiempo de Lamport del generador
                    req.setLamportTime(relojLocal.tick());
                    
                    out.writeObject(req);
                    out.flush();

                    Response response = (Response) in.readObject();
                    
                    // Actualizar el reloj lógico con el retornado por el servidor
                    relojLocal.update(response.getLamportTime());
                    
                    long latencia = System.currentTimeMillis() - inicio;

                    if (response.isSuccess()) {
                        metricas.registrarExito(latencia);
                    } else {
                        // Contar errores de aplicación (saldo insuficiente, juego ya comprado, etc.)
                        metricas.registrarFallo();
                    }
                    
                    // Si nos conectamos con éxito a un nodo secundario, actualizar el índice actual para los siguientes envíos
                    if (index != currentServerIndex.get()) {
                        currentServerIndex.set(index);
                    }
                    return; // Petición exitosa, salir del método
                }
            } catch (Exception e) {
                // Si es el último intento, registrar la petición como fallida
                if (retry == maxRetries - 1) {
                    metricas.registrarFallo();
                }
                // Si no, la excepción se ignora y el bucle pasa al siguiente nodo (failover)
            }
        }
    }

    // Este metodo tiene como objetivo retornar el recolector de metricas para acceso externo
    public ColectorMetricas obtenerMetricas() {
        return metricas;
    }
}
