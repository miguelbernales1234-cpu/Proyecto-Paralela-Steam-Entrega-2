package node;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Semaphore;

/**
 * Algoritmo de Exclusión Mutua Distribuida de Ricart-Agrawala.
 *
 * Protege el recurso crítico: operaciones de escritura en la base de datos
 * compartida (comprarJuego, recargarSaldo).
 *
 * Principio:
 *  - Para entrar a la SC: enviar SOLICITUD (REQUEST) a todos los pares y esperar RESPUESTA (REPLY) de todos.
 *  - Al recibir SOLICITUD ajena: si no quiero entrar → responder RESPUESTA inmediato.
 *    Si quiero entrar y mi timestamp es menor → diferir RESPUESTA hasta salir de SC.
 *  - Al salir de SC: enviar RESPUESTA diferida a todos los que esperaban.
 */
public class RicartAgrawala {

    private final int miId;
    private final RegistroNodos registro;
    private final RelojLamport reloj;

    // Semáforo para bloquear hasta recibir todas las RESPUESTAS necesarias
    private final Semaphore semaforoRespuestas;

    // Cola de nodos a los que debo enviar RESPUESTA diferida
    private final Queue<InfoNodo> respuestasDiferidas = new LinkedList<>();
    private final Object bloqueoDiferidos = new Object();

    // Nodos actualmente marcados como caídos
    private final java.util.Set<Integer> nodosFallidos = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Nodos a los que se les envió una solicitud y de los que se espera respuesta
    private final java.util.Set<Integer> nodosEsperados = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Estado de la solicitud actual
    private volatile boolean deseandoSC    = false; // ¿Quiero entrar a la SC?
    private volatile boolean enSC          = false; // ¿Estoy en la SC?
    private volatile long    miTimestamp   = 0;     // Timestamp de mi última solicitud

    // Objeto de bloqueo para sincronizar variables de estado y evitar condiciones de carrera (deadlocks)
    private final Object lockEstado = new Object();

    // Cuántas RESPUESTAS necesito (= número de pares)
    private int cantidadPares;

    // Contador de mensajes de coordinación generados (para métricas)
    private volatile long contadorMensajesCoordinacion = 0;

    // Este metodo tiene como objetivo inicializar el modulo de exclusion mutua para un nodo
    public RicartAgrawala(int miId, RegistroNodos registro, RelojLamport reloj) {
        this.miId = miId;
        this.registro = registro;
        this.reloj = reloj;
        this.cantidadPares = registro.obtenerPares(miId).size();
        this.semaforoRespuestas = new Semaphore(0);
    }

    /**
     * Solicita acceso a la sección crítica.
     * Bloquea hasta obtener permiso de todos los pares activos.
     */
    public void requestAccess() throws InterruptedException {
        List<InfoNodo> pares = registro.obtenerPares(miId);
        java.util.List<InfoNodo> aEnviar = new java.util.ArrayList<>();
        int nodosAEsperar;

        synchronized (lockEstado) {
            deseandoSC  = true;
            miTimestamp = reloj.tick();
            nodosEsperados.clear();

            // Registrar primero los pares activos que esperaremos
            for (InfoNodo par : pares) {
                int idPar = par.obtenerIdNodo();
                if (nodosFallidos.contains(idPar)) {
                    System.out.println("[RA][Nodo-" + miId + "] Ignorando Nodo-" + idPar + " en solicitud porque está marcado como caído.");
                    continue;
                }
                nodosEsperados.add(idPar);
                aEnviar.add(par);
            }
            nodosAEsperar = nodosEsperados.size();
        }

        System.out.println("[RA][Nodo-" + miId + "] SOLICITANDO SC (lamport=" + miTimestamp + ", esperando=" + nodosAEsperar + ")");

        // Enviar SOLICITUD a todos los pares activos registrados sin retener el lock del estado
        for (InfoNodo par : aEnviar) {
            MensajeNodo req = new MensajeNodo(
                    MensajeNodo.Tipo.RA_SOLICITUD, miId, miTimestamp, miTimestamp);
            enviarAPar(par, req);
            contadorMensajesCoordinacion++;
        }

        // Esperar RESPUESTA de todos los pares activos
        semaforoRespuestas.acquire(nodosAEsperar);
        synchronized (lockEstado) {
            enSC = true;
        }
        System.out.println("[RA][Nodo-" + miId + "] ENTRANDO A SC (lamport=" + reloj.obtenerTiempo() + ")");
    }

    /**
     * Libera la sección crítica y envía RESPUESTAS diferidas a los que esperaban.
     */
    public void releaseAccess() {
        synchronized (lockEstado) {
            enSC       = false;
            deseandoSC = false;
        }
        System.out.println("[RA][Nodo-" + miId + "] SALIENDO DE SC (lamport=" + reloj.obtenerTiempo() + ")");

        // Enviar RESPUESTAS diferidas a todos los que esperaban
        synchronized (bloqueoDiferidos) {
            while (!respuestasDiferidas.isEmpty()) {
                InfoNodo esperando = respuestasDiferidas.poll();
                MensajeNodo respuesta = new MensajeNodo(
                        MensajeNodo.Tipo.RA_RESPUESTA, miId, reloj.tick(), null);
                enviarAPar(esperando, respuesta);
                contadorMensajesCoordinacion++;
            }
        }
    }

    /**
     * Llamado al recibir una SOLICITUD (REQUEST) de otro nodo.
     * Decide si responder de inmediato o diferir el envío.
     */
    public void alRecibirSolicitud(int idEmisor, long timestampEmisor, InfoNodo infoEmisor) {
        reloj.update(timestampEmisor);
        System.out.println("[RA][Nodo-" + miId + "] SOLICITUD RA recibida de Nodo-" + idEmisor
                + " (ts=" + timestampEmisor + ")");

        boolean diferir;
        synchronized (lockEstado) {
            if (enSC) {
                // Estoy en SC → diferir
                diferir = true;
            } else if (deseandoSC) {
                // Quiero entrar → comparar timestamps
                // Si mi timestamp es menor (o igual con menor ID) → diferir
                if (miTimestamp < timestampEmisor) {
                    diferir = true;
                } else if (miTimestamp == timestampEmisor && miId < idEmisor) {
                    diferir = true;
                } else {
                    diferir = false;
                }
            } else {
                // No quiero entrar → responder de inmediato
                diferir = false;
            }
        }

        if (diferir) {
            System.out.println("[RA][Nodo-" + miId + "] Diferiendo RESPUESTA a Nodo-" + idEmisor);
            synchronized (bloqueoDiferidos) {
                respuestasDiferidas.add(infoEmisor);
            }
        } else {
            MensajeNodo respuesta = new MensajeNodo(
                    MensajeNodo.Tipo.RA_RESPUESTA, miId, reloj.tick(), null);
            enviarAPar(infoEmisor, respuesta);
            contadorMensajesCoordinacion++;
        }
    }

    /**
     * Llamado al recibir una RESPUESTA (REPLY) de otro nodo.
     * Incrementa el semáforo para desbloquear requestAccess.
     */
    public void alRecibirRespuesta(int idEmisor, long tiempoRemoto) {
        reloj.update(tiempoRemoto);
        System.out.println("[RA][Nodo-" + miId + "] RESPUESTA RA recibida de Nodo-" + idEmisor
                + " (lamport=" + reloj.obtenerTiempo() + ")");
        boolean esperado;
        synchronized (lockEstado) {
            esperado = nodosEsperados.remove(idEmisor);
        }
        if (esperado) {
            semaforoRespuestas.release();
        }
    }

    /**
     * Llamado cuando un par cae mientras se espera su RESPUESTA.
     * Libera el semáforo para no bloquear indefinidamente.
     */
    public void alDetectarFalloDePar(int idNodoFallido) {
        System.out.println("[RA][Nodo-" + miId + "] Par Nodo-" + idNodoFallido
                + " cayó, liberando espera de RESPUESTA");
        nodosFallidos.add(idNodoFallido);
        boolean esperado = false;
        synchronized (lockEstado) {
            if (deseandoSC) {
                esperado = nodosEsperados.remove(idNodoFallido);
            }
        }
        if (esperado) {
            semaforoRespuestas.release(); // Contar el nodo caído como si hubiera respondido solo si lo esperábamos
        }
        // Limpiar respuestas diferidas para ese nodo
        synchronized (bloqueoDiferidos) {
            respuestasDiferidas.removeIf(n -> n.obtenerIdNodo() == idNodoFallido);
        }
    }

    /**
     * Llamado cuando un par se recupera.
     */
    public void alRecuperarPar(int idNodoRecuperado) {
        System.out.println("[RA][Nodo-" + miId + "] Par Nodo-" + idNodoRecuperado
                + " se ha recuperado, removiendo de lista de caídos");
        nodosFallidos.remove(idNodoRecuperado);
    }

    // Este metodo tiene como objetivo retornar el contador total de mensajes de coordinacion generados
    public long obtenerContadorMensajesCoordinacion() {
        return contadorMensajesCoordinacion;
    }

    // Este metodo tiene como objetivo enviar un MensajeNodo a un nodo destino via socket
    private void enviarAPar(InfoNodo destino, MensajeNodo msg) {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress(destino.obtenerHost(), destino.obtenerPuertoPeer()), 1500);
            try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                out.writeObject(msg);
                out.flush();
            }
        } catch (Exception e) {
            System.err.println("[RA][Nodo-" + miId + "] No se pudo enviar " + msg.obtenerTipo()
                    + " a " + destino + ": " + e.getMessage());
        }
    }
}
