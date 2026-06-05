package node;

/**
 * Reloj de Lamport para ordenamiento causal de eventos distribuidos.
 * Garantiza que si el evento A causa el evento B, entonces lamport(A) < lamport(B).
 * Implementación thread-safe mediante synchronized.
 */
public class RelojLamport {

    private long tiempo;
    private final int idNodo;
    private boolean silencioso = false;

    // Este metodo tiene como objetivo inicializar el reloj de Lamport para un nodo especifico
    public RelojLamport(int idNodo) {
        this.tiempo = 0;
        this.idNodo = idNodo;
    }

    // Constructor para inicializar el reloj con opción de silenciar los logs
    public RelojLamport(int idNodo, boolean silencioso) {
        this.tiempo = 0;
        this.idNodo = idNodo;
        this.silencioso = silencioso;
    }

    /**
     * Incrementa el reloj antes de enviar un mensaje (evento de envio).
     * Retorna el nuevo valor para adjuntarlo al mensaje.
     */
    public synchronized long tick() {
        tiempo++;
        registrar("TICK (envio)");
        return tiempo;
    }

    /**
     * Actualiza el reloj al recibir un mensaje con marca remota.
     * Regla de Lamport: tiempo = max(local, recibido) + 1
     */
    public synchronized long update(long tiempoRecibido) {
        tiempo = Math.max(tiempo, tiempoRecibido) + 1;
        registrar("UPDATE (recepcion, remoto=" + tiempoRecibido + ")");
        return tiempo;
    }

    // Este metodo tiene como objetivo obtener el valor actual del reloj sin modificarlo
    public synchronized long obtenerTiempo() {
        return tiempo;
    }

    // Este metodo tiene como objetivo registrar en consola el estado del reloj con el evento ocurrido
    private void registrar(String evento) {
        if (!silencioso) {
            System.out.printf("[LAMPORT][Nodo-%d] t=%d  evento=%s%n", idNodo, tiempo, evento);
        }
    }
}
