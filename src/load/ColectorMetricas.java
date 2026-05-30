package load;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Recolector de métricas thread-safe para la prueba de carga.
 *
 * Recolecta:
 *  - Throughput: peticiones atendidas / segundo
 *  - Latencia promedio y percentil 95 (p95)
 *  - Tasa de error (requests fallidos / total)
 */
public class ColectorMetricas {

    private final AtomicLong peticionesTotales  = new AtomicLong(0);
    private final AtomicLong peticionesFallidas = new AtomicLong(0);
    private final AtomicLong tiempoInicioMs;

    // Lista de latencias individuales en milisegundos (thread-safe con sincronización)
    private final List<Long> latencias = Collections.synchronizedList(new ArrayList<>());

    // Este metodo tiene como objetivo inicializar el recolector marcando el tiempo de inicio
    public ColectorMetricas() {
        this.tiempoInicioMs = new AtomicLong(System.currentTimeMillis());
    }

    // Este metodo tiene como objetivo registrar la latencia de una peticion exitosa en milisegundos
    public void registrarExito(long latenciaMs) {
        peticionesTotales.incrementAndGet();
        latencias.add(latenciaMs);
    }

    // Este metodo tiene como objetivo registrar una peticion fallida
    public void registrarFallo() {
        peticionesTotales.incrementAndGet();
        peticionesFallidas.incrementAndGet();
    }

    // Este metodo tiene como objetivo calcular el throughput actual en peticiones por segundo
    public double obtenerThroughput() {
        long transcurrido = System.currentTimeMillis() - tiempoInicioMs.get();
        if (transcurrido == 0) return 0;
        return (double) peticionesTotales.get() / (transcurrido / 1000.0);
    }

    // Este metodo tiene como objetivo calcular la latencia promedio en milisegundos
    public double obtenerLatenciaPromedio() {
        synchronized (latencias) {
            if (latencias.isEmpty()) return 0;
            long suma = 0;
            for (long l : latencias) suma += l;
            return (double) suma / latencias.size();
        }
    }

    // Este metodo tiene como objetivo calcular el percentil 95 de latencia en milisegundos
    public long obtenerLatenciaP95() {
        synchronized (latencias) {
            if (latencias.isEmpty()) return 0;
            List<Long> ordenadas = new ArrayList<>(latencias);
            Collections.sort(ordenadas);
            int indice = (int) Math.ceil(0.95 * ordenadas.size()) - 1;
            return ordenadas.get(Math.max(0, indice));
        }
    }

    // Este metodo tiene como objetivo calcular la latencia maxima registrada en milisegundos
    public long obtenerLatenciaMaxima() {
        synchronized (latencias) {
            if (latencias.isEmpty()) return 0;
            return Collections.max(latencias);
        }
    }

    // Este metodo tiene como objetivo calcular la latencia minima registrada en milisegundos
    public long obtenerLatenciaMinima() {
        synchronized (latencias) {
            if (latencias.isEmpty()) return 0;
            return Collections.min(latencias);
        }
    }

    // Este metodo tiene como objetivo retornar el total de peticiones realizadas
    public long obtenerPeticionesTotales() {
        return peticionesTotales.get();
    }

    // Este metodo tiene como objetivo retornar el numero de peticiones fallidas
    public long obtenerPeticionesFallidas() {
        return peticionesFallidas.get();
    }

    // Este metodo tiene como objetivo calcular la tasa de error como porcentaje
    public double obtenerTasaError() {
        long total = peticionesTotales.get();
        if (total == 0) return 0;
        return (double) peticionesFallidas.get() / total * 100.0;
    }

    // Este metodo tiene como objetivo retornar el tiempo transcurrido desde el inicio en segundos
    public double obtenerSegundosTranscurridos() {
        return (System.currentTimeMillis() - tiempoInicioMs.get()) / 1000.0;
    }

    /**
     * Imprime el reporte final de metricas en consola.
     * Incluye una tabla con todos los valores relevantes para el informe.
     */
    public void imprimirReporte() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("        REPORTE DE PRUEBA DE CARGA — Steam Distribuido");
        System.out.println("═".repeat(60));
        System.out.printf("  Duración total      : %.2f s%n", obtenerSegundosTranscurridos());
        System.out.printf("  Peticiones totales  : %d%n", obtenerPeticionesTotales());
        System.out.printf("  Peticiones exitosas : %d%n", obtenerPeticionesTotales() - obtenerPeticionesFallidas());
        System.out.printf("  Peticiones fallidas : %d%n", obtenerPeticionesFallidas());
        System.out.printf("  Tasa de error       : %.2f %%%n", obtenerTasaError());
        System.out.println("─".repeat(60));
        System.out.printf("  Throughput          : %.2f req/s%n", obtenerThroughput());
        System.out.printf("  Latencia mínima     : %d ms%n", obtenerLatenciaMinima());
        System.out.printf("  Latencia promedio   : %.2f ms%n", obtenerLatenciaPromedio());
        System.out.printf("  Latencia P95        : %d ms%n", obtenerLatenciaP95());
        System.out.printf("  Latencia máxima     : %d ms%n", obtenerLatenciaMaxima());
        System.out.println("═".repeat(60));
    }
}
