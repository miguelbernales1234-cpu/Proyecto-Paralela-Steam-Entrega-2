package common;

import java.io.Serializable;
import java.util.ArrayList;

public class Request implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Command {
        CERRAR_CONEXION,
        OBTENER_JUEGOS,
        ELIMINAR_JUEGO,
        BUSCAR_JUEGO,
        CONVERTIR_PRECIO_A_USD,
        BUSCAR_MONEDA,
        GET_PRICES_FROM_MULTIPLE_COUNTRIES,
        GET_PRECIOS_REGIONALES,
        OBTENER_JUEGOS_EN_COMUN,
        OBTENER_PAISES,
        INICIAR_SESION,
        REGISTRAR_USUARIO,
        COMPRAR_JUEGO,
        RECARGAR_SALDO,
        OBTENER_JUEGOS_EN_COMUN_LOCAL,
        OBTENER_BIBLIOTECA
    }

    private Command command;
    private Object[] params;

    // Este metodo tiene como objetivo crear una nueva peticion con un comando y sus parametros
    public Request(Command command, Object... params) {
        this.command = command;
        this.params = params;
    }

    // Este metodo tiene como objetivo retornar el comando de la peticion
    public Command getCommand() {
        return command;
    }

    // Este metodo tiene como objetivo retornar los parametros de la peticion
    public Object[] getParams() {
        return params;
    }
}
