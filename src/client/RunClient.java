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
            System.out.print(" Ingrese IPs/Puertos (Enter = localhost:1009,localhost:1010): ");
            String input = sc.nextLine().trim();
            if (input.isEmpty()) {
                hosts = new String[] { "localhost:1009", "localhost:1010" };
            } else {
                hosts = input.split(",");
            }
        }

        System.out.println(" Iniciando cliente con servidores de respaldo...");
        Client client = new Client();
        client.startClient(hosts);
    }
}
