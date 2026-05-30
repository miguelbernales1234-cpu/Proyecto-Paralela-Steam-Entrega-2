package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonProcessingException;

import common.Juego;
import common.Moneda;
import common.Pais;
import common.PrecioRegional;
import common.Request;
import common.Response;
import common.Usuario;
import node.RelojLamport;
import node.RicartAgrawala;

public class ClientHandler implements Runnable {

    private final Socket clientSocket;
    private final ServerImpl server;
    private final RicartAgrawala ra;   // Null si se usa sin modo distribuido
    private final RelojLamport clock;  // Null si se usa sin modo distribuido

    // Este metodo tiene como objetivo inicializar el manejador en modo distribuido (con RA y reloj de Lamport)
    public ClientHandler(Socket clientSocket, ServerImpl server, RicartAgrawala ra, RelojLamport clock) {
        this.clientSocket = clientSocket;
        this.server       = server;
        this.ra           = ra;
        this.clock        = clock;
    }

    // Este metodo tiene como objetivo inicializar el manejador en modo simple (sin coordinacion distribuida)
    public ClientHandler(Socket clientSocket, ServerImpl server) {
        this(clientSocket, server, null, null);
    }

    @Override
    // Este metodo tiene como objetivo mantener el ciclo de escucha de peticiones del cliente conectado
    public void run() {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        System.out.println("[+] Cliente conectado: " + clientAddress);

        try (
                ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {
            boolean running = true;
            while (running) {
                Request request;
                try {
                    request = (Request) in.readObject();
                } catch (Exception e) {
                    break;
                }

                // Actualizar reloj de Lamport al recibir la peticion del cliente
                if (clock != null) {
                    clock.update(request.getLamportTime());
                }

                Response response;
                try {
                    response = dispatch(request);
                } catch (Exception e) {
                    response = new Response("Error interno del servidor: " + e.getMessage());
                }

                // Marcar la respuesta con el tiempo de Lamport actual antes de enviar
                if (clock != null) {
                    response.setLamportTime(clock.tick());
                }

                out.writeObject(response);
                out.flush();
                out.reset();

                if (request.getCommand() == Request.Command.CERRAR_CONEXION) {
                    running = false;
                }
            }
        } catch (IOException e) {
            System.err.println("[-] Error de I/O con cliente " + clientAddress + ": " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
            System.out.println("[-] Cliente desconectado: " + clientAddress);
        }
    }

    @SuppressWarnings("unchecked")
    // Este metodo tiene como objetivo recibir una peticion, identificar su comando y ejecutar la accion correspondiente en el servidor
    private Response dispatch(Request request) throws Exception {
        Object[] p = request.getParams();

        switch (request.getCommand()) {

            case CERRAR_CONEXION:
                // No se cierra la conexion de la BD aqui porque el servidor es compartido
                // entre multiples clientes. El cierre de la BD ocurre solo al detener el servidor.
                return new Response(true, "Conexion cerrada.");

            case OBTENER_JUEGOS:
                return new Response(true, server.obtenerJuegos());

            case ELIMINAR_JUEGO:
                boolean eliminado = server.eliminarJuego((String) p[0]);
                return new Response(true, eliminado);

            case BUSCAR_JUEGO:
                Juego encontrado = server.buscarJuego((String) p[0]);
                return new Response(true, encontrado);

            case CONVERTIR_PRECIO_A_USD:
                double precioUSD = server.convertirPrecioAUSD((Double) p[0], (String) p[1]);
                return new Response(true, precioUSD);

            case BUSCAR_MONEDA:
                Moneda moneda = server.buscarMoneda((String) p[0]);
                return new Response(true, moneda);

            case GET_PRICES_FROM_MULTIPLE_COUNTRIES:
                ArrayList<Double> precios = server.getPricesFromMultipleCountries(
                        (Integer) p[0], (ArrayList<String>) p[1]);
                return new Response(true, precios);

            case GET_PRECIOS_REGIONALES:
                // Retorna precios con moneda local incluida, tal como los muestra Steam
                ArrayList<PrecioRegional> preciosRegionales = server.getPreciosRegionales(
                        (Integer) p[0], (ArrayList<Pais>) p[1]);
                return new Response(true, preciosRegionales);

            case OBTENER_JUEGOS_EN_COMUN:
                ArrayList<Juego> comunes = server.obtenerJuegosEnComun((ArrayList<String>) p[0]);
                return new Response(true, comunes);

            case OBTENER_PAISES:
                return new Response(true, server.obtenerPaises());

            case INICIAR_SESION:
                Usuario userLogin = server.iniciarSesion((String) p[0], (String) p[1]);
                if (userLogin != null) {
                    return new Response(true, userLogin);
                } else {
                    return new Response("Usuario o contrasena incorrectos.");
                }

            case REGISTRAR_USUARIO:
                Usuario userReg = server.registrarUsuario((String) p[0], (String) p[1], (String) p[2]);
                if (userReg != null) {
                    return new Response(true, userReg);
                } else {
                    return new Response("Error al registrar usuario. El nombre de usuario o correo ya existen.");
                }

            case COMPRAR_JUEGO:
                // RECURSO CRITICO: usar exclusion mutua Ricart-Agrawala en modo distribuido
                if (ra != null) {
                    ra.requestAccess();
                    try {
                        boolean compraOk = server.comprarJuego((Integer) p[0], (Integer) p[1], (Double) p[2]);
                        return new Response(true, compraOk);
                    } finally {
                        ra.releaseAccess();
                    }
                } else {
                    boolean compraOk = server.comprarJuego((Integer) p[0], (Integer) p[1], (Double) p[2]);
                    return new Response(true, compraOk);
                }

            case RECARGAR_SALDO:
                // RECURSO CRITICO: usar exclusion mutua Ricart-Agrawala en modo distribuido
                if (ra != null) {
                    ra.requestAccess();
                    try {
                        double nuevoSaldo = server.recargarSaldo((Integer) p[0], (Double) p[1]);
                        return new Response(true, nuevoSaldo);
                    } finally {
                        ra.releaseAccess();
                    }
                } else {
                    double nuevoSaldo = server.recargarSaldo((Integer) p[0], (Double) p[1]);
                    return new Response(true, nuevoSaldo);
                }

            case OBTENER_JUEGOS_EN_COMUN_LOCAL:
                ArrayList<Juego> comunesLocal = server.obtenerJuegosEnComunLocal((ArrayList<String>) p[0]);
                return new Response(true, comunesLocal);

            case OBTENER_BIBLIOTECA:
                ArrayList<Juego> biblio = server.obtenerBiblioteca((Integer) p[0]);
                return new Response(true, biblio);

            default:
                return new Response("Comando desconocido: " + request.getCommand());
        }
    }
}
