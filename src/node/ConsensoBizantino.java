package node;

import server.ServerImpl;

import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Algoritmo de Consenso Bizantino Simplificado (PBFT-Lite).
 * Permite a los nodos acordar un valor replicado tolerando fallos bizantinos
 * de hasta f = (N-1)/3 nodos.
 * 
 * Flujo:
 * 1. El coordinador inicia el consenso enviando BFT_PROPOSE con un valor.
 * 2. Los nodos validan la propuesta y envían BFT_VOTE a todos si es válida.
 * 3. Si un nodo recibe >= ceil(2N/3) votos por un valor, hace COMMIT local.
 */
public class ConsensoBizantino {

    private final int miId;
    private final RegistroNodos registro;
    private final RelojLamport reloj;
    private final EleccionBully bully;
    private final ServerImpl server;

    // Mapa para rastrear los votos recibidos por cada valor propuesto: Valor -> Set de IDs que votaron
    private final Map<String, Set<Integer>> votosPorValor = new ConcurrentHashMap<>();
    
    // Objeto de bloqueo para evitar que el commit se procese varias veces
    private final Object lockCommit = new Object();
    private String valorComiteado = null;

    public ConsensoBizantino(int miId, RegistroNodos registro, RelojLamport reloj, EleccionBully bully, ServerImpl server) {
        this.miId = miId;
        this.registro = registro;
        this.reloj = reloj;
        this.bully = bully;
        this.server = server;
    }

    /**
     * Inicia el proceso de consenso. Solo debe ser llamado por el coordinador.
     * @param valorPropuesto El nuevo código promocional a replicar.
     */
    public void iniciarConsenso(String valorPropuesto) {
        if (!bully.esCoordinador()) {
            System.err.println("[BFT][Nodo-" + miId + "] Solo el coordinador puede iniciar consenso.");
            return;
        }

        // Limpiamos estado anterior
        votosPorValor.clear();
        valorComiteado = null;

        long t = reloj.tick();
        System.out.println("[BFT][Nodo-" + miId + "] INICIANDO CONSENSO para valor: '" + valorPropuesto + "' (lamport=" + t + ")");

        // Enviar propuesta a todos los pares
        MensajeNodo propuesta = new MensajeNodo(MensajeNodo.Tipo.BFT_PROPOSE, miId, t, valorPropuesto);
        enviarATodos(propuesta);
        
        // Auto-procesar nuestra propia propuesta
        alRecibirPropuesta(miId, valorPropuesto);
    }

    /**
     * Se llama cuando el EscuchadorNodos recibe un BFT_PROPOSE de otro nodo.
     */
    public void alRecibirPropuesta(int idEmisor, String valor) {
        System.out.println("[BFT][Nodo-" + miId + "] Propuesta recibida de Nodo-" + idEmisor + ": '" + valor + "'");

        // Simulación de validación bizantina: el valor no puede estar vacío ni ser nulo
        if (valor == null || valor.trim().isEmpty()) {
            System.err.println("[BFT][Nodo-" + miId + "] Propuesta maliciosa o inválida ignorada.");
            return;
        }

        // Si es válido, emitimos un voto a favor del valor a TODOS los nodos
        long t = reloj.tick();
        System.out.println("[BFT][Nodo-" + miId + "] Emitiendo VOTO para: '" + valor + "'");
        MensajeNodo voto = new MensajeNodo(MensajeNodo.Tipo.BFT_VOTE, miId, t, valor);
        enviarATodos(voto);
        
        // Auto-registrar nuestro propio voto
        alRecibirVoto(miId, valor);
    }

    /**
     * Se llama cuando el EscuchadorNodos recibe un BFT_VOTE de otro nodo.
     */
    public void alRecibirVoto(int idEmisor, String valor) {
        // Inicializar el set si no existe
        votosPorValor.putIfAbsent(valor, Collections.newSetFromMap(new ConcurrentHashMap<>()));
        Set<Integer> votos = votosPorValor.get(valor);
        votos.add(idEmisor);

        System.out.println("[BFT][Nodo-" + miId + "] Voto recibido de Nodo-" + idEmisor + " para: '" + valor + "'. Total votos: " + votos.size());

        verificarQuorum(valor, votos.size());
    }

    private void verificarQuorum(String valor, int cantidadVotos) {
        // N = cantidad de pares + yo
        int totalNodos = registro.obtenerPares(miId).size() + 1;
        
        // Quórum bizantino simplificado: ceil(2N / 3)
        int quorumRequerido = (int) Math.ceil((2.0 * totalNodos) / 3.0);

        if (cantidadVotos >= quorumRequerido) {
            synchronized (lockCommit) {
                // Solo hacer commit si no hemos comiteado ya este valor en esta ronda
                if (!valor.equals(valorComiteado)) {
                    valorComiteado = valor;
                    server.setPromocionGlobal(valor);
                    System.out.println("=================================================");
                    System.out.println("[BFT][Nodo-" + miId + "] *** COMMIT BIZANTINO ALCANZADO ***");
                    System.out.println("[BFT][Nodo-" + miId + "] Nuevo valor replicado: '" + valor + "'");
                    System.out.println("=================================================");
                }
            }
        }
    }

    private void enviarATodos(MensajeNodo msg) {
        List<InfoNodo> pares = registro.obtenerPares(miId);
        for (InfoNodo par : pares) {
            try (Socket s = new Socket()) {
                s.connect(new java.net.InetSocketAddress(par.obtenerHost(), par.obtenerPuertoPeer()), 1500);
                try (ObjectOutputStream out = new ObjectOutputStream(s.getOutputStream())) {
                    out.writeObject(msg);
                    out.flush();
                }
            } catch (Exception e) {
                System.err.println("[BFT][Nodo-" + miId + "] Fallo al enviar " + msg.obtenerTipo() + " a Nodo-" + par.obtenerIdNodo() + ": " + e.getMessage());
            }
        }
    }
}
