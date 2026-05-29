package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RunServer {
    // Este metodo tiene como objetivo iniciar el servidor e instanciar un ClientHandler para cada conexion entrante
    public static void main(String[] args) {
        int port = 1009; // Puerto por defecto
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("Puerto inválido, usando puerto por defecto 1009.");
            }
        }
        
        ServerImpl server = new ServerImpl();

        // Registrar un shutdown hook para cerrar la BD correctamente al detener el servidor
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[!] Cerrando servidor... guardando base de datos.");
            server.cerrarConexion();
        }));

        System.out.println("=== Servidor Steam iniciado en el puerto " + port + " ===");

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Esperando conexiones de clientes...");

            while (true) {
                Socket clientSocket = serverSocket.accept();

                Thread clientThread = new Thread(new ClientHandler(clientSocket, server));
                clientThread.setDaemon(true);
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
