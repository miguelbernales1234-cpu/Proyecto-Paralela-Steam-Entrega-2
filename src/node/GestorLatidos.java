package node;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gestor de heartbeats para detección de fallos y tolerancia a caídas de nodos.
 *
 * - Emisor:   cada INTERVALO_LATIDO_SEG envía un mensaje de LATIDO a todos los pares activos.
 * - Monitor: si pasan más de TIEMPO_LIMITE_SEG sin recibir un LATIDO de un nodo → se marca como caído.
 *            Dispara el algoritmo Bully para re-elegir coordinador.
 */
public class GestorLatidos {

    private static final int INTERVALO_LATIDO_SEG = 2;
    private static final int TIEMPO_LIMITE_SEG    = 6; // 3 latidos perdidos = nodo caído

    private final int miId;
    private final RegistroNodos registro;
    private final RelojLamport reloj;
    private final EleccionBully bully;
    private final RicartAgrawala ra;

    // Último timestamp real de latido recibido de cada par (System.currentTimeMillis)
    private final Map<Integer, Long> ultimoLatidoRecibido = new ConcurrentHashMap<>();

    // Nodos actualmente marcados como caídos
    private final Map<Integer, Boolean> nodosFallidos = new ConcurrentHashMap<>();

    private final ScheduledExecutorService programador = Executors.newScheduledThreadPool(2);

    // Este metodo tiene como objetivo inicializar el gestor de heartbeats para un nodo especifico
    public GestorLatidos(int miId, RegistroNodos registro, RelojLamport reloj,
                         EleccionBully bully, RicartAgrawala ra) {
        this.miId     = miId;
        this.registro = registro;
        this.reloj    = reloj;
        this.bully    = bully;
        this.ra       = ra;

        // Inicializar timestamps de todos los pares como "ahora"
        for (InfoNodo par : registro.obtenerPares(miId)) {
            ultimoLatidoRecibido.put(par.obtenerIdNodo(), System.currentTimeMillis());
            nodosFallidos.put(par.obtenerIdNodo(), false);
        }
    }

    // Este metodo tiene como objetivo iniciar el envio periodico de heartbeats y la supervision de peers
    public void start() {
        // Tarea 1: Enviar latidos periódicos cada INTERVALO_LATIDO_SEG
        programador.scheduleAtFixedRate(this::enviarLatidos,
                1, INTERVALO_LATIDO_SEG, TimeUnit.SECONDS);

        // Tarea 2: Verificar si algún par dejó de responder
        programador.scheduleAtFixedRate(this::supervisarNodos,
                2, INTERVALO_LATIDO_SEG, TimeUnit.SECONDS);

        System.out.println("[HB][Nodo-" + miId + "] GestorLatidos iniciado.");
    }

    // Este metodo tiene como objetivo enviar un latido a todos los pares del cluster
    private void enviarLatidos() {
        long t = reloj.tick();
        for (InfoNodo par : registro.obtenerPares(miId)) {
            if (Boolean.TRUE.equals(nodosFallidos.get(par.obtenerIdNodo()))) continue;
            try (Socket s = new Socket(par.obtenerHost(), par.obtenerPuertoPeer());
                 ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                s.setSoTimeout(1000);
                MensajeNodo latido = new MensajeNodo(MensajeNodo.Tipo.LATIDO, miId, t, null);
                out.writeObject(latido);
                out.flush();
            } catch (Exception e) {
                // Ignorar: el supervisor de pares detectará la caída
            }
        }
    }

    // Este metodo tiene como objetivo verificar si algun par supero el tiempo limite sin responder
    private void supervisarNodos() {
        long ahora = System.currentTimeMillis();
        for (InfoNodo par : registro.obtenerPares(miId)) {
            int idPar = par.obtenerIdNodo();
            long ultimo = ultimoLatidoRecibido.getOrDefault(idPar, ahora);
            long transcurrido = ahora - ultimo;

            if (transcurrido > TIEMPO_LIMITE_SEG * 1000L) {
                if (!Boolean.TRUE.equals(nodosFallidos.get(idPar))) {
                    // Nuevo fallo detectado
                    nodosFallidos.put(idPar, true);
                    System.out.println("[HB][Nodo-" + miId + "] FALLO DETECTADO: Nodo-" + idPar
                            + " sin respuesta por " + (transcurrido / 1000) + "s (lamport=" + reloj.obtenerTiempo() + ")");

                    // Notificar a Ricart-Agrawala para liberar esperas bloqueadas
                    ra.alDetectarFalloDePar(idPar);

                    // Si el nodo caído era el coordinador → iniciar elección Bully
                    if (bully.obtenerIdCoordinador() == idPar) {
                        System.out.println("[HB][Nodo-" + miId + "] El coordinador cayó. Iniciando elección...");
                        new Thread(bully::iniciarEleccion).start();
                    }
                }
            } else {
                // El nodo respondió → si estaba caído, marcarlo como recuperado
                if (Boolean.TRUE.equals(nodosFallidos.get(idPar))) {
                    nodosFallidos.put(idPar, false);
                    System.out.println("[HB][Nodo-" + miId + "] RECUPERACION: Nodo-" + idPar
                            + " volvió a responder (lamport=" + reloj.obtenerTiempo() + ")");
                    ra.alRecuperarPar(idPar);
                }
            }
        }
    }

    // Este metodo tiene como objetivo registrar la recepcion de un heartbeat de un peer
    public void alRecibirLatido(int idEmisor, long tiempoRemoto) {
        reloj.update(tiempoRemoto);
        ultimoLatidoRecibido.put(idEmisor, System.currentTimeMillis());
    }

    // Este metodo tiene como objetivo indicar si un nodo esta marcado como caido
    public boolean esNodoFallido(int idNodo) {
        return Boolean.TRUE.equals(nodosFallidos.get(idNodo));
    }

    // Este metodo tiene como objetivo detener el scheduler de heartbeats
    public void stop() {
        programador.shutdownNow();
    }
}
