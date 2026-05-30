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

    // IDs de juegos populares de Steam para usar en las pruebas
    private static final int[] GAME_IDS = {730, 578080, 1172470, 271590, 892970, 1091500};

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

    // Este metodo tiene como objetivo conectar al nodo, enviar una peticion y registrar la latencia
    private void enviarPeticion(Request req) {
        long inicio = System.currentTimeMillis();
        try (Socket socket = new Socket(host, puerto);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream  in  = new ObjectInputStream(socket.getInputStream())) {

            socket.setSoTimeout(5000); // Timeout de 5 segundos
            out.writeObject(req);
            out.flush();

            Response response = (Response) in.readObject();
            long latencia = System.currentTimeMillis() - inicio;

            if (response.isSuccess()) {
                metricas.registrarExito(latencia);
            } else {
                // Contar errores de aplicación (saldo insuficiente, juego ya comprado, etc.)
                metricas.registrarFallo();
            }
        } catch (Exception e) {
            // Error de red: timeout, conexión rechazada, etc.
            metricas.registrarFallo();
        }
    }

    // Este metodo tiene como objetivo retornar el recolector de metricas para acceso externo
    public ColectorMetricas obtenerMetricas() {
        return metricas;
    }
}
