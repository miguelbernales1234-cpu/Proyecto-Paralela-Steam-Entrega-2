package node;

import java.io.Serializable;

/**
 * Información de un nodo del sistema distribuido.
 * puertoCliente: puerto donde atiende a clientes externos (herramienta de usuario).
 * puertoPeer:    puerto donde atiende mensajes de otros nodos (heartbeats, elección, RA).
 */
public class InfoNodo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int idNodo;
    private final String host;
    private final int puertoCliente;
    private final int puertoPeer;

    // Este metodo tiene como objetivo crear la informacion de un nodo con su identificador, host y puertos
    public InfoNodo(int idNodo, String host, int puertoCliente, int puertoPeer) {
        this.idNodo        = idNodo;
        this.host          = host;
        this.puertoCliente = puertoCliente;
        this.puertoPeer    = puertoPeer;
    }

    // Este metodo tiene como objetivo retornar el identificador unico del nodo
    public int obtenerIdNodo() { return idNodo; }

    // Este metodo tiene como objetivo retornar el host (IP o hostname) del nodo
    public String obtenerHost() { return host; }

    // Este metodo tiene como objetivo retornar el puerto de atencion a clientes externos
    public int obtenerPuertoCliente() { return puertoCliente; }

    // Este metodo tiene como objetivo retornar el puerto de comunicacion entre nodos
    public int obtenerPuertoPeer() { return puertoPeer; }

    @Override
    public String toString() {
        return "Nodo-" + idNodo + "@" + host + " (cliente=" + puertoCliente + ", peer=" + puertoPeer + ")";
    }
}
