package client;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;
import java.util.ArrayList;
import common.Juego;
import common.Pais;
import common.PrecioRegional;
import common.Request;
import common.Response;
import common.Usuario;

public class Client {

    private String[] serverNodes;
    private int currentServerIndex = 0;

    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream  in;
    private Usuario loggedInUser = null;

    // Este metodo tiene como objetivo establecer la conexion con el nodo activo en el cluster
    public boolean connectToActiveNode() {
        for (int i = 0; i < serverNodes.length; i++) {
            int index = (currentServerIndex + i) % serverNodes.length;
            String[] parts = serverNodes[index].split(":");
            String host = parts[0];
            int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 1009;

            try {
                if (socket != null && !socket.isClosed()) socket.close();
                socket = new Socket(host, port);                           
                out    = new ObjectOutputStream(socket.getOutputStream()); 
                in     = new ObjectInputStream(socket.getInputStream());   
                currentServerIndex = index;
                System.out.println(ConsoleUtils.GREEN + " [✔] Conectado al nodo distribuido de Steam: " + host + ":" + port + ConsoleUtils.RESET);
                return true;
            } catch (Exception e) {
                System.out.println(ConsoleUtils.YELLOW + " [!] El nodo " + host + ":" + port + " está caído. Buscando réplica activa..." + ConsoleUtils.RESET);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    // Este metodo tiene como objetivo enviar una peticion al servidor con tolerancia a fallos activa (Failover)
    private Response sendRequest(Request request) throws Exception {
        int maxRetries = serverNodes.length;
        for (int i = 0; i < maxRetries; i++) {
            try {
                if (socket == null || socket.isClosed()) {
                    if (!connectToActiveNode()) throw new Exception("Todos los servidores del clúster están caídos.");
                }
                out.writeObject(request); 
                out.flush();
                out.reset();
                return (Response) in.readObject(); 
            } catch (Exception e) {
                System.out.println(ConsoleUtils.RED + " [x] Fallo en el nodo actual detectado. Iniciando Failover..." + ConsoleUtils.RESET);
                if (socket != null && !socket.isClosed()) socket.close();
                socket = null; // Fuerza la reconexión en la siguiente iteración
            }
        }
        throw new Exception("Error Crítico: Ningún nodo del clúster pudo procesar la petición.");
    }

    // Este metodo tiene como objetivo iniciar el ciclo principal de interaccion con el usuario en consola
    public void startClient(String[] hosts) {
        this.serverNodes = hosts;
        if (!connectToActiveNode()) {
            System.out.println(ConsoleUtils.RED + " [Error] Imposible conectar al clúster de Steam. Saliendo..." + ConsoleUtils.RESET);
            return;
        }

        try {

            Scanner sc = new Scanner(System.in);
            int opcion = -1;

            while (opcion != 0) {
                ConsoleUtils.clearScreen();
                ConsoleUtils.printBanner();

                if (loggedInUser == null) {
                    // MENÚ DE INVITADO (SIN SESIÓN)
                    System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(48) + ConsoleUtils.TOP_RIGHT);
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[1]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Iniciar Sesión");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[2]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Registrar Cuenta Nueva");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[3]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Ver catálogo de juegos");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[4]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Buscar juego por nombre");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[0]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Salir");
                    System.out.println(" " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(48) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);

                    System.out.print("\n" + ConsoleUtils.BOLD + " Ingrese una opción: " + ConsoleUtils.RESET);

                    if (sc.hasNextInt()) {
                        opcion = sc.nextInt();
                        sc.nextLine();
                    } else {
                        System.out.println(ConsoleUtils.RED + "Debes ingresar un número válido." + ConsoleUtils.RESET);
                        sc.nextLine();
                        ConsoleUtils.promptEnterKey(sc);
                        continue;
                    }

                    System.out.println();

                    switch (opcion) {
                        case 1: iniciarSesion(sc);             break;
                        case 2: registrarUsuario(sc);           break;
                        case 3: listarJuegos();                 break;
                        case 4: buscarJuego(sc);                break;
                        case 0:
                            System.out.println(ConsoleUtils.GREEN + "Cerrando cliente. ¡Hasta luego!" + ConsoleUtils.RESET);
                            break;
                        default:
                            System.out.println(ConsoleUtils.RED + "Opción no reconocida. Intenta nuevamente." + ConsoleUtils.RESET);
                    }
                } else {
                    // MENÚ DE USUARIO AUTENTICADO
                    System.out.println(ConsoleUtils.GREEN + ConsoleUtils.BOLD + "   Sesión activa: " + loggedInUser.getUsername() + " | Billetera: $" + String.format("%.2f", loggedInUser.getWalletBalance()) + " USD" + ConsoleUtils.RESET);
                    System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(48) + ConsoleUtils.TOP_RIGHT);
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[1]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Ver mi Perfil y Billetera");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[2]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Ver mi Biblioteca de Juegos");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[3]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Comprar un Juego");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[4]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Juegos en Común con Amigos (BD Local)");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[5]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Recargar Saldo");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[6]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Cerrar Sesión");
                    System.out.printf(" " + ConsoleUtils.VERTICAL + "  " + ConsoleUtils.YELLOW + "[0]" + ConsoleUtils.RESET + " %-42s" + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n", "Salir");
                    System.out.println(" " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(48) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);

                    System.out.print("\n" + ConsoleUtils.BOLD + " Ingrese una opción: " + ConsoleUtils.RESET);

                    if (sc.hasNextInt()) {
                        opcion = sc.nextInt();
                        sc.nextLine();
                    } else {
                        System.out.println(ConsoleUtils.RED + "Debes ingresar un número válido." + ConsoleUtils.RESET);
                        sc.nextLine();
                        ConsoleUtils.promptEnterKey(sc);
                        continue;
                    }

                    System.out.println();

                    switch (opcion) {
                        case 1: verPerfil();                         break;
                        case 2: verMiBiblioteca();                   break;
                        case 3: comprarJuego(sc);                    break;
                        case 4: buscarJuegosEnComunLocal(sc);        break;
                        case 5: recargarSaldo(sc);                   break;
                        case 6:
                            loggedInUser = null;
                            System.out.println(ConsoleUtils.GREEN + "Sesión cerrada correctamente." + ConsoleUtils.RESET);
                            break;
                        case 0:
                            System.out.println(ConsoleUtils.GREEN + "Cerrando cliente. ¡Hasta luego!" + ConsoleUtils.RESET);
                            break;
                        default:
                            System.out.println(ConsoleUtils.RED + "Opción no reconocida. Intenta nuevamente." + ConsoleUtils.RESET);
                    }
                }

                if (opcion != 0) {
                    ConsoleUtils.promptEnterKey(sc);
                }
            }

            sendRequest(new Request(Request.Command.CERRAR_CONEXION));
            sc.close();
            socket.close();

        } catch (Exception e) {
            System.err.println("Error al iniciar cliente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Este metodo tiene como objetivo pedir al servidor y mostrar el catalogo de juegos registrados
    private void listarJuegos() {
        ConsoleUtils.printHeader("Catálogo de Juegos Steam");
        try {
            Response response = sendRequest(new Request(Request.Command.OBTENER_JUEGOS));
            if (!response.isSuccess()) {
                System.out.println(ConsoleUtils.RED + "Error del servidor: " + response.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            @SuppressWarnings("unchecked")
            ArrayList<Juego> games = (ArrayList<Juego>) response.getResult();
            if (games.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + "No hay juegos registrados en el catálogo." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(73) + ConsoleUtils.TOP_RIGHT + ConsoleUtils.RESET);
                System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.BOLD + " %-10s | %-45s | %-10s " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET, "App ID", "Nombre", "Tipo");
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.HORIZONTAL.repeat(73) + ConsoleUtils.VERTICAL + ConsoleUtils.RESET);
                for (Juego j : games) {
                    String tipoColor = "game".equalsIgnoreCase(j.getTipo()) ? ConsoleUtils.GREEN : ConsoleUtils.YELLOW;
                    System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.RESET + " %-10d | %-45s | " + tipoColor + "%-10s" + ConsoleUtils.RESET + " " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET,
                            j.getId(), j.getNombre(), j.getTipo() != null ? j.getTipo() : "game");
                }
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(73) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);
            }
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + "Error al obtener catálogo de juegos: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este metodo tiene como objetivo solicitar un nombre y pedir al servidor buscar un juego especifico
    private void buscarJuego(Scanner sc) {
        ConsoleUtils.printHeader("Buscar Juego");
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese el nombre del juego a buscar: " + ConsoleUtils.RESET);
            String nombre = sc.nextLine();

            Response response = sendRequest(new Request(Request.Command.BUSCAR_JUEGO, nombre));
            if (!response.isSuccess()) {
                System.out.println(ConsoleUtils.RED + "\n Error del servidor: " + response.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            Juego juego = (Juego) response.getResult();
            if (juego != null) {
                String tipo = juego.getTipo() != null ? juego.getTipo() : "game";
                System.out.println(ConsoleUtils.GREEN + "\n ¡Juego encontrado!" + ConsoleUtils.RESET);
                System.out.println(ConsoleUtils.CYAN + "   Nombre : " + ConsoleUtils.RESET + juego.getNombre());
                System.out.println(ConsoleUtils.CYAN + "   App ID : " + ConsoleUtils.RESET + juego.getId());
                System.out.println(ConsoleUtils.CYAN + "   Tipo   : " + ConsoleUtils.RESET + tipo);
            } else {
                System.out.println(ConsoleUtils.RED + "\n No se encontró el juego: " + nombre + ConsoleUtils.RESET);
            }
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + "\n Error al buscar juego: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este metodo tiene como objetivo solicitar los IDs de Steam y encontrar juegos que todos los usuarios poseen,
    // permitiendo identificar que pueden jugar juntos
    private void buscarJuegosEnComun(Scanner sc) {
        ConsoleUtils.printHeader("Juegos en Común con Amigos");
        System.out.println(ConsoleUtils.GRAY + " Ingresa los Steam IDs de tus amigos para ver qué juegos tienen todos en común." + ConsoleUtils.RESET);
        System.out.println(ConsoleUtils.GRAY + " (Ideal para decidir qué jugar juntos. Los perfiles deben ser públicos.)" + ConsoleUtils.RESET);
        System.out.println();
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese cantidad de jugadores (mínimo 2): " + ConsoleUtils.RESET);
            int cantidad = 0;
            if (sc.hasNextInt()) {
                cantidad = sc.nextInt();
                sc.nextLine();
            } else {
                System.out.println(ConsoleUtils.RED + " Debe ingresar un número válido." + ConsoleUtils.RESET);
                sc.nextLine();
                return;
            }

            if (cantidad < 2) {
                System.out.println(ConsoleUtils.RED + " Debe ingresar al menos 2 jugadores." + ConsoleUtils.RESET);
                return;
            }

            ArrayList<String> steamIds = new ArrayList<>();
            for (int i = 0; i < cantidad; i++) {
                System.out.print(" Ingrese el Steam ID del jugador " + (i + 1) + ": ");
                steamIds.add(sc.nextLine().trim());
            }

            System.out.println(ConsoleUtils.BLUE + "\n Consultando en paralelo las bibliotecas de " + cantidad + " perfiles de Steam..." + ConsoleUtils.RESET);
            long startTime = System.currentTimeMillis();

            Response response = sendRequest(new Request(Request.Command.OBTENER_JUEGOS_EN_COMUN, steamIds));
            long endTime = System.currentTimeMillis();

            if (!response.isSuccess()) {
                System.out.println(ConsoleUtils.RED + "\n Error del servidor: " + response.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            @SuppressWarnings("unchecked")
            ArrayList<Juego> comunes = (ArrayList<Juego>) response.getResult();

            if (comunes.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + "\n No se encontraron juegos en común." + ConsoleUtils.RESET);
                System.out.println(ConsoleUtils.GRAY + " Verifica que los perfiles sean públicos y que la API Key sea válida." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.GREEN + "\n ¡Encontrados " + comunes.size() + " juego(s) que todos poseen!" + ConsoleUtils.RESET);
                System.out.println(ConsoleUtils.GRAY + " Estos son los juegos que pueden jugar juntos:" + ConsoleUtils.RESET);
                System.out.println();
                for (int i = 0; i < comunes.size(); i++) {
                    System.out.println(ConsoleUtils.CYAN + " " + (i + 1) + ".- " + ConsoleUtils.RESET + comunes.get(i).getNombre() + ConsoleUtils.GRAY + " (App ID: " + comunes.get(i).getId() + ")" + ConsoleUtils.RESET);
                }
            }
            System.out.println(ConsoleUtils.GRAY + "\n Tiempo de consulta: " + (endTime - startTime) + "ms (consultas paralelas)" + ConsoleUtils.RESET);
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + "\n Error al buscar juegos en común: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este metodo tiene como objetivo solicitar un juego y mostrar su precio en cada region de Steam
    // con la moneda local y el equivalente en USD, tal como lo muestra Steam
    private void compararPrecioEnRegiones(Scanner sc) {
        ConsoleUtils.printHeader("Precios por Región (Steam)");
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese el nombre del juego a comparar: " + ConsoleUtils.RESET);
            String nombre = sc.nextLine();

            Response rJuego = sendRequest(new Request(Request.Command.BUSCAR_JUEGO, nombre));
            Juego juego = (Juego) rJuego.getResult();
            if (juego == null) {
                System.out.println(ConsoleUtils.RED + "\n No se encontró el juego: " + nombre + ConsoleUtils.RESET);
                return;
            }

            Response rPaises = sendRequest(new Request(Request.Command.OBTENER_PAISES));
            if (!rPaises.isSuccess()) {
                System.out.println(ConsoleUtils.RED + "\n Error al obtener países: " + rPaises.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            @SuppressWarnings("unchecked")
            ArrayList<Pais> paisesBD = (ArrayList<Pais>) rPaises.getResult();
            if (paisesBD.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + "\n No hay países registrados en la base de datos." + ConsoleUtils.RESET);
                return;
            }

            System.out.println(ConsoleUtils.BLUE + "\n Consultando precios en " + paisesBD.size() + " regiones de Steam..." + ConsoleUtils.RESET);
            long startTime = System.currentTimeMillis();

            // Usar el nuevo comando que retorna precios con moneda local incluida
            Response rPrecios = sendRequest(new Request(Request.Command.GET_PRECIOS_REGIONALES,
                    juego.getId(), paisesBD));
            long endTime = System.currentTimeMillis();

            if (!rPrecios.isSuccess()) {
                System.out.println(ConsoleUtils.RED + "\n Error del servidor: " + rPrecios.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            
            @SuppressWarnings("unchecked")
            ArrayList<PrecioRegional> precios = (ArrayList<PrecioRegional>) rPrecios.getResult();

            System.out.println("\n " + ConsoleUtils.BOLD + "Precios de: " + ConsoleUtils.YELLOW + juego.getNombre() + ConsoleUtils.RESET);

            // Encabezado de la tabla
            System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(37) + ConsoleUtils.TOP_RIGHT + ConsoleUtils.RESET);
            System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.BOLD + " %-18s | %-14s " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET,
                    "País", "Equiv. USD");
            System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.HORIZONTAL.repeat(37) + ConsoleUtils.VERTICAL + ConsoleUtils.RESET);

            // Encontrar precio minimo en USD para resaltarlo
            double minUSD = Double.MAX_VALUE;
            int minIndex = -1;
            for (int i = 0; i < precios.size(); i++) {
                PrecioRegional p = precios.get(i);
                if (p.getPrecioUSD() > 0 && p.getPrecioUSD() < minUSD) {
                    minUSD = p.getPrecioUSD();
                    minIndex = i;
                }
            }

            for (int i = 0; i < precios.size(); i++) {
                PrecioRegional p = precios.get(i);
                String color = (i == minIndex) ? ConsoleUtils.GREEN : ConsoleUtils.RESET;
                String usdStr;
                if (p.getPrecioUSD() < 0) {
                    usdStr = "N/A";
                } else if (p.getPrecioUSD() == 0) {
                    usdStr = "Gratis";
                } else {
                    usdStr = String.format("$%.2f USD", p.getPrecioUSD());
                }
                String marker = (i == minIndex) ? " ◄ MÁS BARATO" : "";
                System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + color + " %-18s | %-14s " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + color + "%s\n" + ConsoleUtils.RESET,
                        p.getPaisNombre(), usdStr, marker);
            }
            System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(37) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);
            System.out.println(ConsoleUtils.GRAY + " Tiempo de consulta: " + (endTime - startTime) + "ms (consultas paralelas a la API de Steam)" + ConsoleUtils.RESET);

        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + "\n Error al comparar precios: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // --- MÉTODOS PARA RÉPLICA AUTÓNOMA DE STEAM ---

    // Este método tiene como objetivo autenticar a un usuario local en el sistema
    private void iniciarSesion(Scanner sc) {
        ConsoleUtils.printHeader("Iniciar Sesión en Steam");
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese su nombre de usuario: " + ConsoleUtils.RESET);
            String username = sc.nextLine().trim();

            System.out.print(ConsoleUtils.BOLD + " Ingrese su contraseña: " + ConsoleUtils.RESET);
            String password = sc.nextLine().trim();

            Response rLogin = sendRequest(new Request(Request.Command.INICIAR_SESION, username, password));
            if (rLogin.isSuccess()) {
                loggedInUser = (Usuario) rLogin.getResult();
                System.out.println(ConsoleUtils.GREEN + "\n [✔] ¡Inicio de sesión exitoso! Bienvenido, " + loggedInUser.getUsername() + "." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.RED + "\n [x] Error: " + rLogin.getErrorMessage() + ConsoleUtils.RESET);
            }
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error al iniciar sesión: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este método tiene como objetivo registrar un nuevo usuario en la base de datos local
    private void registrarUsuario(Scanner sc) {
        ConsoleUtils.printHeader("Registrar Nueva Cuenta de Steam");
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese un nombre de usuario nuevo: " + ConsoleUtils.RESET);
            String username = sc.nextLine().trim();

            System.out.print(ConsoleUtils.BOLD + " Ingrese su contraseña: " + ConsoleUtils.RESET);
            String password = sc.nextLine().trim();

            System.out.print(ConsoleUtils.BOLD + " Ingrese su correo electrónico: " + ConsoleUtils.RESET);
            String email = sc.nextLine().trim();

            // Obtener países disponibles para selección
            Response rPaises = sendRequest(new Request(Request.Command.OBTENER_PAISES));
            @SuppressWarnings("unchecked")
            ArrayList<Pais> paisesBD = (ArrayList<Pais>) rPaises.getResult();

            String codigoPais = "";
            while (true) {
                System.out.println("\n " + ConsoleUtils.BOLD + "Países disponibles:" + ConsoleUtils.RESET);
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(31) + ConsoleUtils.TOP_RIGHT + ConsoleUtils.RESET);
                System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.BOLD + " %-20s | %-6s " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET, "País", "ID");
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.HORIZONTAL.repeat(31) + ConsoleUtils.VERTICAL + ConsoleUtils.RESET);
                for (Pais p : paisesBD) {
                    System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.RESET + " %-20s | %-6s " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET,
                            p.getNombre(), p.getId());
                }
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(31) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);

                System.out.print("\n" + ConsoleUtils.BOLD + " Ingrese el ID de su país: " + ConsoleUtils.RESET);
                codigoPais = sc.nextLine().trim().toLowerCase();
                
                boolean finalValid = false;
                for (Pais p : paisesBD) {
                    if (p.getId().equalsIgnoreCase(codigoPais)) {
                        finalValid = true;
                        codigoPais = p.getId(); // mantener el formato de la base de datos
                        break;
                    }
                }
                if (finalValid) {
                    break;
                }
                System.out.println(ConsoleUtils.RED + " ID de país no válido. Intente nuevamente." + ConsoleUtils.RESET);
            }

            Response rReg = sendRequest(new Request(Request.Command.REGISTRAR_USUARIO, username, password, email, codigoPais));
            if (rReg.isSuccess()) {
                loggedInUser = (Usuario) rReg.getResult();
                System.out.println(ConsoleUtils.GREEN + "\n [✔] ¡Registro exitoso! Se ha iniciado sesión automáticamente." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.RED + "\n [x] Error al registrarse: " + rReg.getErrorMessage() + ConsoleUtils.RESET);
            }
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error al registrar usuario: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este método tiene como objetivo mostrar los detalles de perfil del usuario logueado
    private void verPerfil() {
        ConsoleUtils.printHeader("Mi Perfil de Steam");
        System.out.println(ConsoleUtils.CYAN + "   Nombre de Usuario : " + ConsoleUtils.RESET + loggedInUser.getUsername());
        System.out.println(ConsoleUtils.CYAN + "   Correo Electrónico: " + ConsoleUtils.RESET + loggedInUser.getEmail());
        System.out.println(ConsoleUtils.CYAN + "   Saldo Billetera   : " + ConsoleUtils.GREEN + "$" + String.format("%.2f", loggedInUser.getWalletBalance()) + " USD" + ConsoleUtils.RESET);
    }

    // Este método tiene como objetivo consultar y listar los juegos propiedad del usuario
    private void verMiBiblioteca() {
        ConsoleUtils.printHeader("Mi Biblioteca de Juegos");
        try {
            Response response = sendRequest(new Request(Request.Command.OBTENER_BIBLIOTECA, loggedInUser.getId()));
            if (!response.isSuccess()) {
                System.out.println(ConsoleUtils.RED + " Error del servidor: " + response.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }
            @SuppressWarnings("unchecked")
            ArrayList<Juego> biblioteca = (ArrayList<Juego>) response.getResult();
            if (biblioteca.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + " No posees juegos en tu biblioteca. ¡Ve a la tienda a comprar algunos!" + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.TOP_LEFT + ConsoleUtils.HORIZONTAL.repeat(63) + ConsoleUtils.TOP_RIGHT + ConsoleUtils.RESET);
                for (int i = 0; i < biblioteca.size(); i++) {
                    System.out.printf(ConsoleUtils.CYAN + " " + ConsoleUtils.VERTICAL + ConsoleUtils.RESET + "  %-3d.- %-35s (App ID: %-8d) " + ConsoleUtils.CYAN + ConsoleUtils.VERTICAL + "\n" + ConsoleUtils.RESET,
                            (i + 1), biblioteca.get(i).getNombre(), biblioteca.get(i).getId());
                }
                System.out.println(ConsoleUtils.CYAN + " " + ConsoleUtils.BOTTOM_LEFT + ConsoleUtils.HORIZONTAL.repeat(63) + ConsoleUtils.BOTTOM_RIGHT + ConsoleUtils.RESET);
            }
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error al obtener biblioteca: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este método tiene como objetivo simular una compra de la tienda usando precios regionales en USD
    private void comprarJuego(Scanner sc) {
        ConsoleUtils.printHeader("Tienda Steam - Comprar Juego");
        listarJuegos();
        System.out.println();
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese el App ID del juego que desea comprar: " + ConsoleUtils.RESET);
            int gameId = Integer.parseInt(sc.nextLine());

            // Buscar juego en el catálogo local
            Response rJuego = sendRequest(new Request(Request.Command.BUSCAR_JUEGO, String.valueOf(gameId)));
            Juego juego = (Juego) rJuego.getResult();
            if (juego == null) {
                System.out.println(ConsoleUtils.RED + " [x] El juego no existe en el catálogo." + ConsoleUtils.RESET);
                return;
            }

            // Consultar países de la base de datos
            Response rPaises = sendRequest(new Request(Request.Command.OBTENER_PAISES));
            @SuppressWarnings("unchecked")
            ArrayList<Pais> paisesBD = (ArrayList<Pais>) rPaises.getResult();
            if (paisesBD.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + " No hay países registrados para calcular precios regionales." + ConsoleUtils.RESET);
                return;
            }

            // Buscar el país del usuario logueado en la base de datos
            Pais paisUsuario = null;
            for (Pais p : paisesBD) {
                if (p.getId().equalsIgnoreCase(loggedInUser.getCodigoPais())) {
                    paisUsuario = p;
                    break;
                }
            }

            if (paisUsuario == null) {
                System.out.println(ConsoleUtils.RED + " [x] Tu cuenta está asociada a un país no válido: " + loggedInUser.getCodigoPais() + ConsoleUtils.RESET);
                return;
            }

            // Consultar precio únicamente para la región del usuario logueado
            System.out.println(ConsoleUtils.BLUE + " Consultando precio regional para tu región (" + paisUsuario.getNombre() + ")..." + ConsoleUtils.RESET);
            ArrayList<Pais> paisesConsulta = new ArrayList<>();
            paisesConsulta.add(paisUsuario);
            Response rPrecios = sendRequest(new Request(Request.Command.GET_PRECIOS_REGIONALES, gameId, paisesConsulta));
            @SuppressWarnings("unchecked")
            ArrayList<PrecioRegional> precios = (ArrayList<PrecioRegional>) rPrecios.getResult();

            if (precios.isEmpty() || precios.get(0).getPrecioUSD() < 0) {
                System.out.println(ConsoleUtils.RED + " [x] El juego no está disponible en tu región (" + paisUsuario.getNombre() + ")." + ConsoleUtils.RESET);
                return;
            }

            PrecioRegional seleccionado = precios.get(0);
            double precioUSD = seleccionado.getPrecioUSD();
            System.out.printf(ConsoleUtils.GREEN + " Precio para tu región (%s): %s -> Equivalente a $%.2f USD\n" + ConsoleUtils.RESET,
                    seleccionado.getPaisNombre(), seleccionado.getPrecioFormateado(), precioUSD);

            System.out.print(ConsoleUtils.BOLD + " ¿Confirmar compra de \"" + juego.getNombre() + "\"? (S/N): " + ConsoleUtils.RESET);
            String confirmStr = sc.nextLine().trim();
            if (!confirmStr.equalsIgnoreCase("S")) {
                System.out.println(ConsoleUtils.YELLOW + " Compra cancelada por el usuario." + ConsoleUtils.RESET);
                return;
            }

            Response rCompra = sendRequest(new Request(Request.Command.COMPRAR_JUEGO, loggedInUser.getId(), gameId, precioUSD));
            if (rCompra.isSuccess() && (Boolean) rCompra.getResult()) {
                System.out.println(ConsoleUtils.GREEN + " [✔] ¡Compra exitosa! El juego se ha añadido a tu biblioteca." + ConsoleUtils.RESET);
                loggedInUser.setWalletBalance(loggedInUser.getWalletBalance() - precioUSD);
            } else {
                System.out.println(ConsoleUtils.RED + " [x] Error al comprar: " + rCompra.getErrorMessage() + ConsoleUtils.RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(ConsoleUtils.RED + " El ID debe ser un número entero." + ConsoleUtils.RESET);
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este método tiene como objetivo buscar juegos comunes entre perfiles de la base de datos de manera paralela
    private void buscarJuegosEnComunLocal(Scanner sc) {
        ConsoleUtils.printHeader("Juegos en Común (BD Local)");
        System.out.println(ConsoleUtils.GRAY + " Encuentra qué juegos tienen en común tú y tus amigos usando hilos paralelos de base de datos." + ConsoleUtils.RESET);
        System.out.println();
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese cantidad de amigos a buscar: " + ConsoleUtils.RESET);
            int cantidad = Integer.parseInt(sc.nextLine());
            if (cantidad < 1) {
                System.out.println(ConsoleUtils.RED + " Debe ingresar al menos 1 amigo." + ConsoleUtils.RESET);
                return;
            }

            ArrayList<String> usernames = new ArrayList<>();
            // Añadir el usuario actual automáticamente
            usernames.add(loggedInUser.getUsername());

            for (int i = 0; i < cantidad; i++) {
                System.out.print(" Ingrese el nombre de usuario del amigo " + (i + 1) + ": ");
                usernames.add(sc.nextLine().trim());
            }

            System.out.println(ConsoleUtils.BLUE + "\n Consultando en paralelo las bibliotecas locales en la base de datos..." + ConsoleUtils.RESET);
            long startTime = System.currentTimeMillis();
            Response response = sendRequest(new Request(Request.Command.OBTENER_JUEGOS_EN_COMUN_LOCAL, usernames));
            long endTime = System.currentTimeMillis();

            if (!response.isSuccess()) {
                System.out.println(ConsoleUtils.RED + " Error del servidor: " + response.getErrorMessage() + ConsoleUtils.RESET);
                return;
            }

            @SuppressWarnings("unchecked")
            ArrayList<Juego> comunes = (ArrayList<Juego>) response.getResult();
            if (comunes.isEmpty()) {
                System.out.println(ConsoleUtils.YELLOW + "\n No se encontraron juegos en común localmente." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.GREEN + "\n ¡Encontrados " + comunes.size() + " juego(s) en común!" + ConsoleUtils.RESET);
                System.out.println();
                for (int i = 0; i < comunes.size(); i++) {
                    System.out.println(ConsoleUtils.CYAN + " " + (i + 1) + ".- " + ConsoleUtils.RESET + comunes.get(i).getNombre() + ConsoleUtils.GRAY + " (App ID: " + comunes.get(i).getId() + ")" + ConsoleUtils.RESET);
                }
            }
            System.out.println(ConsoleUtils.GRAY + "\n Tiempo de consulta en BD (Paralela): " + (endTime - startTime) + "ms" + ConsoleUtils.RESET);
        } catch (NumberFormatException e) {
            System.out.println(ConsoleUtils.RED + " Debe ingresar un número entero." + ConsoleUtils.RESET);
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este método tiene como objetivo simular una recarga económica a la billetera virtual del usuario
    private void recargarSaldo(Scanner sc) {
        ConsoleUtils.printHeader("Recargar Saldo de Billetera");
        try {
            System.out.print(ConsoleUtils.BOLD + " Ingrese el monto en USD a recargar: " + ConsoleUtils.RESET);
            double monto = Double.parseDouble(sc.nextLine());

            Response response = sendRequest(new Request(Request.Command.RECARGAR_SALDO, loggedInUser.getId(), monto));
            if (response.isSuccess()) {
                double nuevoSaldo = (Double) response.getResult();
                loggedInUser.setWalletBalance(nuevoSaldo);
                System.out.println(ConsoleUtils.GREEN + " [✔] Recarga exitosa. Tu nuevo saldo es: $" + String.format("%.2f", nuevoSaldo) + " USD." + ConsoleUtils.RESET);
            } else {
                System.out.println(ConsoleUtils.RED + " [x] Error al recargar saldo: " + response.getErrorMessage() + ConsoleUtils.RESET);
            }
        } catch (NumberFormatException e) {
            System.out.println(ConsoleUtils.RED + " El monto debe ser un valor decimal válido." + ConsoleUtils.RESET);
        } catch (Exception e) {
            System.out.println(ConsoleUtils.RED + " Error al procesar recarga: " + e.getMessage() + ConsoleUtils.RESET);
        }
    }

    // Este metodo tiene como objetivo instanciar e iniciar el cliente
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : "localhost";
        Client cliente = new Client();
        cliente.startClient(new String[]{host});
    }
}
