package node;

import java.io.Serializable;

/**
 * Mensaje intercambiado entre nodos del sistema distribuido.
 * Lleva siempre la marca de reloj de Lamport del nodo emisor.
 */
public class MensajeNodo implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Tipo {
        LATIDO,         // Señal de vida periódica (Heartbeat)
        ELECCION,       // Inicio de elección Bully
        OK,             // Respuesta OK en Bully (hay nodo con mayor ID activo)
        COORDINADOR,    // Anuncio del nuevo coordinador
        RA_SOLICITUD,   // Solicitud de acceso a sección crítica (Ricart-Agrawala)
        RA_RESPUESTA,   // Respuesta de permiso (Ricart-Agrawala)
        SINC_ESTADO     // Sincronización de estado al reintegrarse un nodo
    }

    private final Tipo tipo;
    private final int idEmisor;
    private final long tiempoLamport;
    private final Object cargaUtil;   // Datos adicionales (payload) según el tipo de mensaje

    // Este metodo tiene como objetivo crear un mensaje entre nodos con tipo, emisor, marca Lamport y carga util
    public MensajeNodo(Tipo tipo, int idEmisor, long tiempoLamport, Object cargaUtil) {
        this.tipo          = tipo;
        this.idEmisor      = idEmisor;
        this.tiempoLamport = tiempoLamport;
        this.cargaUtil     = cargaUtil;
    }

    // Este metodo tiene como objetivo retornar el tipo de mensaje entre nodos
    public Tipo obtenerTipo() { return tipo; }

    // Este metodo tiene como objetivo retornar el ID del nodo que envio este mensaje
    public int obtenerIdEmisor() { return idEmisor; }

    // Este metodo tiene como objetivo retornar la marca de reloj de Lamport del emisor
    public long obtenerTiempoLamport() { return tiempoLamport; }

    // Este metodo tiene como objetivo retornar la carga util adicional del mensaje
    public Object obtenerCargaUtil() { return cargaUtil; }

    @Override
    public String toString() {
        return "[MensajeNodo tipo=" + tipo + " emisor=Nodo-" + idEmisor + " lamport=" + tiempoLamport + "]";
    }
}
