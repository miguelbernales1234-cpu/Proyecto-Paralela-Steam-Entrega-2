package client;

import java.util.Scanner;

public class ConsoleUtils {
    // Colores ANSI
    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String GRAY = "\u001B[90m";
    
    public static final String BOLD = "\u001B[1m";

    // Caracteres para bordes
    public static final String TOP_LEFT = "╔";
    public static final String TOP_RIGHT = "╗";
    public static final String BOTTOM_LEFT = "╚";
    public static final String BOTTOM_RIGHT = "╝";
    public static final String HORIZONTAL = "═";
    public static final String VERTICAL = "║";

    // Este metodo tiene como objetivo limpiar la pantalla de la consola
    public static void clearScreen() {
        // Códigos ANSI para limpiar pantalla
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    // Este metodo tiene como objetivo pausar la ejecucion hasta que el usuario presione ENTER
    public static void promptEnterKey(Scanner sc) {
        System.out.println();
        System.out.println(GRAY + "Presione ENTER para continuar..." + RESET);
        sc.nextLine();
    }

    // Este metodo tiene como objetivo imprimir el banner principal de la aplicacion
    public static void printBanner() {
        System.out.println(CYAN + BOLD);
        System.out.println("   _____  _______  _______  _______  __   __ ");
        System.out.println("  |       ||       ||       ||   _   ||  |_|  |");
        System.out.println("  |  _____||_     _||    ___||  |_|  ||       |");
        System.out.println("  | |_____   |   |  |   |___ |       ||       |");
        System.out.println("  |_____  |  |   |  |    ___||       ||       |");
        System.out.println("   _____| |  |   |  |   |___ |   _   || ||_|| |");
        System.out.println("  |_______|  |___|  |_______||__| |__||_|   |_|");
        System.out.println("                                               ");
        System.out.println("  STEAM — Plataforma de Juegos Distribuida     ");
        System.out.println("  Catálogo · Precios Regionales · Bibliotecas  ");
        System.out.println(RESET);
    }
    
    // Este metodo tiene como objetivo imprimir un encabezado con un titulo centrado
    public static void printHeader(String title) {
        int width = 50;
        int padding = (width - title.length()) / 2;
        String line = HORIZONTAL.repeat(width);
        
        System.out.println(CYAN + TOP_LEFT + line + TOP_RIGHT);
        
        System.out.print(VERTICAL);
        for(int i=0; i<padding; i++) System.out.print(" ");
        System.out.print(BOLD + WHITE + title + RESET + CYAN);
        
        // compensar si es impar
        int rightPadding = width - title.length() - padding;
        for(int i=0; i<rightPadding; i++) System.out.print(" ");
        System.out.println(VERTICAL);
        
        System.out.println(BOTTOM_LEFT + line + BOTTOM_RIGHT + RESET);
    }
}
