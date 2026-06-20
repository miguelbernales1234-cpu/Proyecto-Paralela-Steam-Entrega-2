package node;

import server.ServerImpl;
import server.ClientHandler;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Nodo principal del sistema distribuido Steam.
 *
 * Integra todos los componentes:
 *  - ServerImpl:       lógica de negocio (juegos, precios, usuarios)
 *  - EscuchadorNodos:  atiende mensajes P2P de otros nodos
 *  - GestorLatidos:    detecta fallos y dispara re-elección
 *  - EleccionBully:    elige coordinador distribuido
 *  - RicartAgrawala:   garantiza exclusión mutua en operaciones críticas
 *  - ClientHandler:    atiende clientes externos (misma interfaz que antes)
 *
 * Lanzar con:
 *   java node.NodoPeer <idNodo> <puertoCliente> <puertoPeer> [archivoNodosConfig]
 */
public class NodoPeer {

    private final int idNodo;
    private final int puertoCliente;
    private final int puertoPeer;

    private final ServerImpl server;
    private final RegistroNodos registro;
    private final RelojLamport reloj;
    private final EleccionBully bully;
    private final RicartAgrawala ra;
    private final GestorLatidos hbManager;
    private final ConsensoBizantino bft;
    private final EscuchadorNodos peerListener;

    // Este metodo tiene como objetivo construir el nodo distribuido con todos sus modulos de coordinacion
    public NodoPeer(int idNodo, int puertoCliente, int puertoPeer, String archivoNodosConfig) {
        this.idNodo        = idNodo;
        this.puertoCliente = puertoCliente;
        this.puertoPeer    = puertoPeer;

        System.out.println("=== [Nodo-" + idNodo + "] Iniciando sistema distribuido Steam ===");

        // 1. Lógica de negocio (base de datos, precios, usuarios)
        this.server   = new ServerImpl();

        // 2. Registro de todos los nodos del cluster
        this.registro = new RegistroNodos(archivoNodosConfig);

        // 3. Reloj de Lamport compartido entre todos los módulos
        this.reloj    = new RelojLamport(idNodo);

        // 4. Algoritmo Bully para elección de coordinador
        this.bully    = new EleccionBully(idNodo, registro, reloj);

        // 5. Exclusión mutua Ricart-Agrawala para operaciones críticas
        this.ra       = new RicartAgrawala(idNodo, registro, reloj);

        // 6. Heartbeats: detecta caídas y dispara Bully
        this.hbManager = new GestorLatidos(idNodo, registro, reloj, bully, ra);

        // Módulo BFT: Consenso Bizantino para replicación de valor global
        this.bft = new ConsensoBizantino(idNodo, registro, reloj, bully, server);

        // 7. Listener P2P: recibe y enruta mensajes de otros nodos
        this.peerListener = new EscuchadorNodos(idNodo, puertoPeer, bully, ra, hbManager, registro, bft);
    }

    /**
     * Arranca todos los componentes del nodo en hilos separados y
     * comienza a atender clientes externos en puertoCliente.
     */
    public void start() {
        // Hilo 1: Listener P2P (comunicación entre nodos)
        Thread hiloPeer = new Thread(peerListener, "EscuchadorNodos-" + idNodo);
        hiloPeer.setDaemon(true);
        hiloPeer.start();

        // Hilo 2: GestorLatidos (envío y recepción de latidos)
        hbManager.start();

        // Esperar un momento para que el listener P2P esté listo antes de enviar heartbeats
        try { Thread.sleep(500); } catch (InterruptedException ignored) {}

        // Iniciar elección para determinar coordinador inicial
        System.out.println("[Nodo-" + idNodo + "] Iniciando elección inicial...");
        new Thread(bully::iniciarEleccion).start();

        // Registrar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Nodo-" + idNodo + "] Cerrando nodo...");
            hbManager.stop();
            server.cerrarConexion();
        }));

        // Hilo 3: Servidor de clientes externos (mismo protocolo que antes)
        iniciarServidorClientes();
    }

    // Este metodo tiene como objetivo iniciar el ServerSocket que atiende clientes externos
    private void iniciarServidorClientes() {
        System.out.println("[Nodo-" + idNodo + "] Esperando clientes en puerto " + puertoCliente + "...");
        try (ServerSocket ss = new ServerSocket(puertoCliente)) {
            while (true) {
                Socket socketCliente = ss.accept();
                // Pasar la referencia de ra, reloj y bft al ClientHandler
                ClientHandler handler = new ClientHandler(socketCliente, server, ra, reloj, bft);
                Thread t = new Thread(handler, "ClientHandler-" + idNodo);
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            System.err.println("[Nodo-" + idNodo + "] Error en servidor de clientes: " + e.getMessage());
        }
    }

    // Este metodo tiene como objetivo exponer el reloj de Lamport del nodo
    public RelojLamport getReloj() { return reloj; }

    // Este metodo tiene como objetivo exponer el modulo de eleccion Bully
    public EleccionBully getBully() { return bully; }

    // Este metodo tiene como objetivo exponer el modulo de exclusion mutua RA
    public RicartAgrawala getRa() { return ra; }

    // Este metodo tiene como objetivo retornar el ID de este nodo
    public int getNodeId() { return idNodo; }

    /**
     * Punto de entrada principal.
     * Uso: java node.NodoPeer <idNodo> <puertoCliente> <puertoPeer> [nodes.txt]
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Uso: java node.NodoPeer <idNodo> <puertoCliente> <puertoPeer> [archivoNodosConfig]");
            System.err.println("Ejemplo: java node.NodoPeer 1 5000 6000 nodes.txt");
            System.exit(1);
        }
        int    idNodo        = Integer.parseInt(args[0]);
        int    puertoCliente = Integer.parseInt(args[1]);
        int    puertoPeer    = Integer.parseInt(args[2]);
        String archivoConfig = args.length > 3 ? args[3] : "nodes.txt";

        NodoPeer nodo = new NodoPeer(idNodo, puertoCliente, puertoPeer, archivoConfig);
        nodo.start();
    }
}
