package client;

import java.util.Scanner;

public class RunClient {
    public static void main(String[] args) {
        String[] hosts;

        if (args.length > 0) {
            hosts = args[0].split(",");
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.println(" Puede ingresar varios servidores separados por coma.");
            System.out.print(" Ingrese IPs/Puertos o presione Enter para conectarse a los puertos distribuidores por defecto (localhost:5000, localhost:5001, localhost:5002): ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                hosts = new String[] { "localhost:5000", "localhost:5001", "localhost:5002" };
            } else {
                hosts = input.split(",");
            }
        }

        System.out.println(" Iniciando cliente con servidores de respaldo...");
        Client client = new Client();
        client.startClient(hosts);
    }
}
