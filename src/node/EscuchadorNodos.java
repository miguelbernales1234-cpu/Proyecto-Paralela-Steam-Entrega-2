package node;

import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Servidor P2P que escucha mensajes de otros nodos en el puerto puertoPeer.
 * Por cada conexión entrante de un nodo par, deserializa el MensajeNodo
 * y lo despacha al manejador correspondiente (Bully, RA, Latidos).
 */
public class EscuchadorNodos implements Runnable {

    private final int miId;
    private final int puertoPeer;
    private final EleccionBully bully;
    private final RicartAgrawala ra;
    private final GestorLatidos hbManager;
    private final RegistroNodos registro;
    private final ConsensoBizantino bft;
    private volatile boolean activo = true;

    // Este metodo tiene como objetivo inicializar el listener P2P con todos los modulos de coordinacion
    public EscuchadorNodos(int miId, int puertoPeer, EleccionBully bully,
                           RicartAgrawala ra, GestorLatidos hbManager, RegistroNodos registro, ConsensoBizantino bft) {
        this.miId      = miId;
        this.puertoPeer = puertoPeer;
        this.bully     = bully;
        this.ra        = ra;
        this.hbManager = hbManager;
        this.registro  = registro;
        this.bft       = bft;
    }

    @Override
    // Este metodo tiene como objetivo aceptar conexiones P2P y procesar cada mensaje en un hilo separado
    public void run() {
        try (ServerSocket ss = new ServerSocket(puertoPeer)) {
            System.out.println("[EscuchadorNodos][Nodo-" + miId + "] Escuchando pares en puerto " + puertoPeer);
            while (activo) {
                try {
                    Socket socket = ss.accept();
                    // Cada mensaje se procesa en su propio hilo para no bloquear el listener principal
                    Thread t = new Thread(() -> procesarConexionPeer(socket));
                    t.setDaemon(true);
                    t.start();
                } catch (Exception e) {
                    if (activo) {
                        System.err.println("[EscuchadorNodos][Nodo-" + miId + "] Error aceptando: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[EscuchadorNodos][Nodo-" + miId + "] Error al iniciar: " + e.getMessage());
        }
    }

    // Este metodo tiene como objetivo leer y despachar un mensaje recibido de otro nodo
    private void procesarConexionPeer(Socket socket) {
        try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
            MensajeNodo msg = (MensajeNodo) in.readObject();
            despachar(msg);
        } catch (Exception e) {
            // Conexión cerrada normalmente, ignorar
        } finally {
            try { socket.close(); } catch (Exception ignored) {}
        }
    }

    // Este metodo tiene como objetivo enrutar el mensaje al modulo de coordinacion correcto segun su tipo
    private void despachar(MensajeNodo msg) {
        int idEmisor = msg.obtenerIdEmisor();
        long lamport = msg.obtenerTiempoLamport();

        switch (msg.obtenerTipo()) {

            case LATIDO:
                hbManager.alRecibirLatido(idEmisor, lamport);
                break;

            case ELECCION:
                // Respondemos OK si tenemos mayor ID (lo hace EleccionBully internamente)
                InfoNodo infoEmisor = registro.obtenerNodo(idEmisor);
                bully.alRecibirEleccion(idEmisor, infoEmisor);
                break;

            case OK:
                bully.alRecibirOK(idEmisor);
                break;

            case COORDINADOR:
                int nuevoCoordId = (msg.obtenerCargaUtil() instanceof Integer)
                        ? (Integer) msg.obtenerCargaUtil() : idEmisor;
                bully.alRecibirCoordinador(nuevoCoordId, lamport);
                break;

            case RA_SOLICITUD:
                long timestampEmisor = (msg.obtenerCargaUtil() instanceof Long)
                        ? (Long) msg.obtenerCargaUtil() : lamport;
                InfoNodo solicitante = registro.obtenerNodo(idEmisor);
                ra.alRecibirSolicitud(idEmisor, timestampEmisor, solicitante);
                break;

            case RA_RESPUESTA:
                ra.alRecibirRespuesta(idEmisor, lamport);
                break;

            case SINC_ESTADO:
                System.out.println("[EscuchadorNodos][Nodo-" + miId + "] SINC_ESTADO recibido de Nodo-" + idEmisor);
                break;

            case BFT_PROPOSE:
                if (msg.obtenerCargaUtil() instanceof String) {
                    bft.alRecibirPropuesta(idEmisor, (String) msg.obtenerCargaUtil());
                }
                break;

            case BFT_VOTE:
                if (msg.obtenerCargaUtil() instanceof String) {
                    bft.alRecibirVoto(idEmisor, (String) msg.obtenerCargaUtil());
                }
                break;

            default:
                System.err.println("[EscuchadorNodos][Nodo-" + miId + "] Tipo desconocido: " + msg.obtenerTipo());
        }
    }

    // Este metodo tiene como objetivo detener el listener P2P
    public void stop() {
        activo = false;
    }
}
