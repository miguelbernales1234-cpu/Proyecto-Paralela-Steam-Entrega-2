package node;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Algoritmo de elección de coordinador Bully.
 *
 * Reglas:
 *  1. Cuando un nodo detecta que el coordinador cayó, inicia elección.
 *  2. Envía ELECCION a todos los nodos con ID mayor.
 *  3. Si recibe OK de alguno → espera COORDINADOR (un nodo con mayor ID se hará cargo).
 *  4. Si nadie responde en TIEMPO_ESPERA_MS → se declara coordinador y envía COORDINADOR a todos.
 *  5. Al recibir ELECCION de un nodo con menor ID → responde OK y lanza su propia elección.
 *  6. Al recibir COORDINADOR → actualiza coordinador actual y resetea la elección.
 */
public class EleccionBully {

    private static final long TIEMPO_ESPERA_MS = 3000; // 3 segundos para esperar respuesta OK

    private final int miId;
    private final RegistroNodos registro;
    private final RelojLamport reloj;
    private final AtomicInteger idCoordinador;
    private final AtomicBoolean eleccionEnProgreso;
    private final AtomicBoolean recibidoOK;
    private final AtomicInteger idEleccionActual = new AtomicInteger(0);

    // Este metodo tiene como objetivo inicializar el modulo de eleccion Bully para un nodo especifico
    public EleccionBully(int miId, RegistroNodos registro, RelojLamport reloj) {
        this.miId               = miId;
        this.registro           = registro;
        this.reloj              = reloj;
        this.idCoordinador      = new AtomicInteger(miId); // Asumir que soy coordinador al inicio
        this.eleccionEnProgreso = new AtomicBoolean(false);
        this.recibidoOK         = new AtomicBoolean(false);
    }

    /**
     * Inicia una nueva elección Bully.
     * Llamado cuando se detecta que el coordinador actual cayó.
     */
    public void iniciarEleccion() {
        if (eleccionEnProgreso.getAndSet(true)) {
            return; // Ya hay una elección en progreso
        }
        recibidoOK.set(false);
        int rondaElecta = idEleccionActual.incrementAndGet();

        long t = reloj.tick();
        System.out.println("[BULLY][Nodo-" + miId + "] INICIANDO ELECCION (ronda=" + rondaElecta + ", lamport=" + t + ")");

        List<InfoNodo> nodosMayores = registro.obtenerNodosConIdMayor(miId);

        if (nodosMayores.isEmpty()) {
            // Soy el nodo con mayor ID → me declaro coordinador inmediatamente
            declararCoordinador();
            return;
        }

        // Enviar ELECCION a todos los nodos con mayor ID
        for (InfoNodo destino : nodosMayores) {
            enviarMensaje(destino, new MensajeNodo(MensajeNodo.Tipo.ELECCION, miId, reloj.tick(), null));
        }

        // Esperar TIEMPO_ESPERA_MS por respuesta OK
        Thread temporizador = new Thread(() -> {
            try {
                Thread.sleep(TIEMPO_ESPERA_MS);
                if (rondaElecta != idEleccionActual.get()) return;
                if (!recibidoOK.get()) {
                    // Nadie respondió → me declaro coordinador
                    declararCoordinador();
                } else {
                    // Alguien con mayor ID respondió → esperar que anuncie COORDINADOR
                    System.out.println("[BULLY][Nodo-" + miId + "] Recibí OK, esperando COORDINADOR...");
                    Thread.sleep(TIEMPO_ESPERA_MS * 2);
                    if (rondaElecta != idEleccionActual.get()) return;
                    if (eleccionEnProgreso.get()) {
                        // El nodo mayor tampoco anunció → reiniciar elección
                        eleccionEnProgreso.set(false);
                        iniciarEleccion();
                    }
                }
            } catch (InterruptedException ignored) {}
        });
        temporizador.setDaemon(true);
        temporizador.start();
    }

    // Este metodo tiene como objetivo declararse coordinador y notificar a todos los nodos del cluster
    private void declararCoordinador() {
        idCoordinador.set(miId);
        eleccionEnProgreso.set(false);
        long t = reloj.tick();
        System.out.println("[BULLY][Nodo-" + miId + "] SOY EL NUEVO COORDINADOR (lamport=" + t + ")");

        for (InfoNodo par : registro.obtenerPares(miId)) {
            enviarMensaje(par, new MensajeNodo(MensajeNodo.Tipo.COORDINADOR, miId, reloj.tick(), miId));
        }
    }

    /**
     * Llamado cuando este nodo recibe un mensaje ELECCION de idEmisor.
     * Como mi ID es mayor → respondo OK y lanzo mi propia elección.
     */
    public void alRecibirEleccion(int idEmisor, InfoNodo infoEmisor) {
        long t = reloj.update(0);
        System.out.println("[BULLY][Nodo-" + miId + "] ELECCION recibida de Nodo-" + idEmisor + " (lamport=" + t + ")");
        // Responder OK al nodo que inició la elección
        if (infoEmisor != null) {
            enviarMensaje(infoEmisor, new MensajeNodo(MensajeNodo.Tipo.OK, miId, reloj.tick(), null));
        }
        // Lanzar mi propia elección si no hay una en progreso
        if (!eleccionEnProgreso.get()) {
            iniciarEleccion();
        }
    }

    /**
     * Llamado cuando este nodo recibe un mensaje OK de un nodo con mayor ID.
     * Significa que hay alguien con mayor ID activo que se encargará de ser coordinador.
     */
    public void alRecibirOK(int idEmisor) {
        long t = reloj.update(0);
        System.out.println("[BULLY][Nodo-" + miId + "] OK recibido de Nodo-" + idEmisor + " (lamport=" + t + ")");
        recibidoOK.set(true);
    }

    /**
     * Llamado cuando este nodo recibe un anuncio COORDINADOR.
     * Actualiza el coordinador vigente y termina la elección.
     */
    public void alRecibirCoordinador(int nuevoIdCoordinador, long tiempoRemoto) {
        reloj.update(tiempoRemoto);
        idCoordinador.set(nuevoIdCoordinador);
        eleccionEnProgreso.set(false);
        System.out.println("[BULLY][Nodo-" + miId + "] Nuevo coordinador: Nodo-" + nuevoIdCoordinador
                + " (lamport=" + reloj.obtenerTiempo() + ")");
    }

    // Este metodo tiene como objetivo retornar el ID del coordinador vigente
    public int obtenerIdCoordinador() {
        return idCoordinador.get();
    }

    // Este metodo tiene como objetivo indicar si este nodo es el coordinador actual
    public boolean esCoordinador() {
        return idCoordinador.get() == miId;
    }

    // Este metodo tiene como objetivo marcar que la eleccion ha finalizado (reset)
    public void reiniciarEleccion() {
        eleccionEnProgreso.set(false);
    }

    // Este metodo tiene como objetivo enviar un MensajeNodo a un nodo destino via socket
    private void enviarMensaje(InfoNodo destino, MensajeNodo msg) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(destino.obtenerHost(), destino.obtenerPuertoPeer()), 1500);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("[BULLY][Nodo-" + miId + "] No se pudo contactar a " + destino + ": " + e.getMessage());
        }
    }
}
