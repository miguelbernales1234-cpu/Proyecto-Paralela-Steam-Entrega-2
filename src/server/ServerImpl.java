package server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import common.InterfazDeServer;
import common.Juego;
import common.Moneda;
import common.Pais;
import common.PrecioRegional;
import common.Usuario;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ServerImpl implements InterfazDeServer {
    private ArrayList<Juego> BD_juegos = new ArrayList<>();
    private ArrayList<Pais> BD_paises = new ArrayList<>();
    private ArrayList<Moneda> BD_moneda = new ArrayList<>();
    private Connection connection = null;
    private static final String STEAM_API_KEY = System.getenv("STEAM_API_KEY") != null
            ? System.getenv("STEAM_API_KEY")
            : "8D9E6D169F3A14A3D20CEA4A6E289CCC"; // Fallback: definir la variable de entorno STEAM_API_KEY

    // Parámetros de base de datos configurables vía variables de entorno
    private static final String DB_HOST = System.getenv("DB_HOST") != null ? System.getenv("DB_HOST") : "localhost";
    private static final String DB_PORT = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
    private static final String DB_NAME = System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "project_db_extended";
    private static final String DB_USER = System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root";
    private static final String DB_PASS = System.getenv("DB_PASS") != null ? System.getenv("DB_PASS") : "";
    private static final String DB_URL  = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME;

    // Este metodo tiene como objetivo inicializar el servidor y conectarlo a la
    // base de datos
    public ServerImpl() {
        conectarBD();
    }

    @Override
    // Este metodo tiene como objetivo obtener y convertir el precio de un juego
    // desde la API de Steam a USD
    public double getPriceFromApiSteam(int id_juego, String id_pais) {
        String output = null;
        try {
            URL apiUrl = new URL(
                    "https://store.steampowered.com/api/appdetails?appids=" + id_juego + "&cc=" + id_pais + "&l=es");
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();

            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                in.close();
                output = response.toString();
            } else {
                System.out.println("Error al conectar a la API. Código de respuesta: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ObjectMapper objectMapper = new ObjectMapper();

        try {
            JsonNode jsonNode = objectMapper.readTree(output);
            String appIdStr = String.valueOf(id_juego);
            JsonNode appData = jsonNode.get(appIdStr);
            if (appData != null && appData.has("data")) {
                JsonNode dataNode = appData.get("data");
                if (dataNode.has("price_overview")) {
                    String currency = dataNode.get("price_overview").get("currency").asText();
                    double precioLocal = dataNode.get("price_overview").get("final").asDouble() / 100.0;
                    double precioEnUSD = convertirPrecioAUSD(precioLocal, currency);
                    return precioEnUSD;
                } else {
                    boolean isFree = dataNode.has("is_free") && dataNode.get("is_free").asBoolean();
                    if (isFree) {
                        return 0.0;
                    } else {
                        return 29.99; // Fallback para comerciales sin precio directo (como GTA V)
                    }
                }
            } else {
                System.out.println("appData es null o no tiene data.");
            }
        } catch (JsonMappingException e) {
            e.printStackTrace();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return 0;
    }

    @Override
    // Este metodo tiene como objetivo obtener los precios en USD de un juego para
    // multiples paises de manera concurrente
    public ArrayList<Double> getPricesFromMultipleCountries(int id_juego, ArrayList<String> id_paises) {
        int numThreads = Math.min(id_paises.size(), 20);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            List<CompletableFuture<Double>> futures = id_paises.stream()
                    .map(pais -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return getPriceFromApiSteam(id_juego, pais);
                        } catch (Exception e) {
                            System.err.println("Error obteniendo precio para " + pais + ": " + e.getMessage());
                            return 0.0;
                        }
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
            allOf.join();

            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toCollection(ArrayList::new));
        } finally {
            executor.shutdown();
        }
    }

    // Este metodo tiene como objetivo obtener el precio de un juego en una region
    // especifica,
    // retornando tanto el precio en moneda local (como lo muestra Steam) como su
    // equivalente en USD
    private PrecioRegional getPrecioRegionalDeApiSteam(int id_juego, Pais pais) {
        String output = null;
        try {
            URL apiUrl = new URL(
                    "https://store.steampowered.com/api/appdetails?appids=" + id_juego + "&cc=" + pais.getId()
                            + "&l=es");
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null)
                    sb.append(line);
                br.close();
                output = sb.toString();
            } else {
                System.out.println("Error al conectar a la API para " + pais.getNombre() + ". Código: " + responseCode);
            }
        } catch (Exception e) {
            System.err.println("Excepción obteniendo precio para " + pais.getNombre() + ": " + e.getMessage());
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(output);
            String appIdStr = String.valueOf(id_juego);
            JsonNode appData = jsonNode.get(appIdStr);
            if (appData != null && appData.has("data")) {
                JsonNode dataNode = appData.get("data");
                if (dataNode.has("price_overview")) {
                    JsonNode priceNode = dataNode.get("price_overview");
                    String moneda = priceNode.get("currency").asText();
                    double precioLocal = priceNode.get("final").asDouble() / 100.0;
                    // Steam retorna el precio ya formateado en la moneda local del pais
                    String formateado = priceNode.has("final_formatted")
                            ? priceNode.get("final_formatted").asText()
                            : String.format("%.2f %s", precioLocal, moneda);
                    if ("USD".equalsIgnoreCase(moneda) && precioLocal > 0) {
                        formateado = String.format("USD $%,.2f", precioLocal);
                    }
                    double precioUSD = convertirPrecioAUSD(precioLocal, moneda);
                    return new PrecioRegional(pais.getNombre(), moneda, precioLocal, formateado, precioUSD);
                } else {
                    boolean isFree = dataNode.has("is_free") && dataNode.get("is_free").asBoolean();
                    if (isFree) {
                        return new PrecioRegional(pais.getNombre(), "N/A", 0.0, "Gratis", 0.0);
                    } else {
                        // Fallback de precio para juegos comerciales sin precio en la API (como GTA V)
                        String moneda = getCurrencyForCountry(pais.getId());
                        Moneda mon = buscarMoneda(moneda);
                        double ratio = (mon != null) ? mon.getUSDRatio() : 1.0;
                        double precioUSD = 29.99; // precio base coherente
                        double precioLocal = Math.round((precioUSD / ratio) * 100.0) / 100.0;
                        String formateado = formatLocalPrice(precioLocal, moneda);
                        return new PrecioRegional(pais.getNombre(), moneda, precioLocal, formateado, precioUSD);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error procesando precio para " + pais.getNombre() + ": " + e.getMessage());
        }
        // No disponible en esta region
        return new PrecioRegional(pais.getNombre(), "N/A", -1.0, "No disponible", -1.0);
    }

    @Override
    // Este metodo tiene como objetivo obtener los precios de un juego en multiples
    // paises de manera
    // concurrente, incluyendo el precio en moneda local tal como lo muestra Steam
    public ArrayList<PrecioRegional> getPreciosRegionales(int id_juego, ArrayList<Pais> paises) {
        int numThreads = Math.min(paises.size(), 20);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        try {
            List<CompletableFuture<PrecioRegional>> futures = paises.stream()
                    .map(pais -> CompletableFuture.supplyAsync(
                            () -> getPrecioRegionalDeApiSteam(id_juego, pais), executor))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toCollection(ArrayList::new));
        } finally {
            executor.shutdown();
        }
    }

    @Override
    // Este metodo tiene como objetivo obtener los precios de todos los juegos del catalogo en un pais de manera concurrente
    public ArrayList<PrecioRegional> getPreciosCatalogo(ArrayList<Juego> juegos, Pais pais) {
        int numThreads = Math.min(juegos.size(), 20);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        try {
            List<CompletableFuture<PrecioRegional>> futures = juegos.stream()
                    .map(juego -> CompletableFuture.supplyAsync(
                            () -> getPrecioRegionalDeApiSteam(juego.getId(), pais), executor))
                    .collect(Collectors.toList());
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            return futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toCollection(ArrayList::new));
        } finally {
            executor.shutdown();
        }
    }

    // Este metodo tiene como objetivo obtener todos los juegos que posee un usuario
    // de Steam especifico
    private ArrayList<Juego> consultarJuegosDelPerfil(String steamId) {
        ArrayList<Juego> juegosDelPerfil = new ArrayList<>();
        String output = null;
        try {
            URL apiUrl = new URL("http://api.steampowered.com/IPlayerService/GetOwnedGames/v0001/?key=" + STEAM_API_KEY
                    + "&steamid=" + steamId + "&format=json&include_appinfo=1");
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("GET");
            int responseCode = conn.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                output = response.toString();
            } else {
                System.out.println(
                        "Error al conectar a GetOwnedGames para SteamID " + steamId + ". Código: " + responseCode);
                return juegosDelPerfil;
            }
        } catch (Exception e) {
            System.err.println("Excepción consultando SteamID " + steamId + ": " + e.getMessage());
            return juegosDelPerfil;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode rootNode = objectMapper.readTree(output);
            JsonNode responseNode = rootNode.get("response");
            if (responseNode != null && responseNode.has("games")) {
                JsonNode gamesNode = responseNode.get("games");
                for (JsonNode gameNode : gamesNode) {
                    int appId = gameNode.get("appid").asInt();
                    String name = gameNode.has("name") ? gameNode.get("name").asText() : "Desconocido";
                    juegosDelPerfil.add(new Juego(name, appId));
                }
            } else {
                System.out.println("No se encontraron juegos para " + steamId
                        + " (puede que el perfil sea privado o la API Key sea inválida).");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return juegosDelPerfil;
    }

    @Override
    // Este metodo tiene como objetivo encontrar la interseccion de juegos entre
    // varios perfiles de Steam de manera concurrente
    public ArrayList<Juego> obtenerJuegosEnComun(ArrayList<String> steamIds) {
        if (steamIds == null || steamIds.isEmpty())
            return new ArrayList<>();

        int numThreads = Math.min(steamIds.size(), 10);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            List<CompletableFuture<ArrayList<Juego>>> futures = steamIds.stream()
                    .map(id -> CompletableFuture.supplyAsync(() -> consultarJuegosDelPerfil(id), executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ArrayList<Juego>> todasLasBibliotecas = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            if (todasLasBibliotecas.isEmpty())
                return new ArrayList<>();

            ArrayList<Juego> interseccion = new ArrayList<>(todasLasBibliotecas.get(0));

            for (int i = 1; i < todasLasBibliotecas.size(); i++) {
                java.util.HashSet<Integer> idsActuales = new java.util.HashSet<>();
                for (Juego j : todasLasBibliotecas.get(i)) {
                    idsActuales.add(j.getId());
                }

                interseccion.removeIf(juegoBase -> !idsActuales.contains(juegoBase.getId()));
            }

            return interseccion;
        } finally {
            executor.shutdown();
        }
    }

    @Override
    // Este metodo tiene como objetivo devolver la lista de juegos sincronizada
    // directamente con la base de datos para consistencia distribuida
    public synchronized ArrayList<Juego> obtenerJuegos() {
        ArrayList<Juego> juegos = new ArrayList<>();
        String sql = "SELECT * FROM juegos";
        try (Statement query = connection.createStatement();
                ResultSet resultados = query.executeQuery(sql)) {
            while (resultados.next()) {
                int id = resultados.getInt("id");
                String nombre = resultados.getString("nombre");
                juegos.add(new Juego(nombre, id));
            }
            BD_juegos = juegos; // Sincronizar memoria
        } catch (SQLException e) {
            System.err.println("Error al obtener juegos de la base de datos: " + e.getMessage());
        }
        return BD_juegos;
    }

    @Override
    // Este metodo tiene como objetivo devolver la lista de paises sincronizada
    // directamente con la base de datos para consistencia distribuida
    public synchronized ArrayList<Pais> obtenerPaises() {
        ArrayList<Pais> paises = new ArrayList<>();
        String sql = "SELECT * FROM paises";
        try (Statement query = connection.createStatement();
                ResultSet resultados = query.executeQuery(sql)) {
            while (resultados.next()) {
                String id = resultados.getString("codigo_pais");
                String nombre = resultados.getString("nombre_pais");
                paises.add(new Pais(nombre, id));
            }
            BD_paises = paises; // Sincronizar memoria
        } catch (SQLException e) {
            System.err.println("Error al obtener paises de la base de datos: " + e.getMessage());
        }
        return BD_paises;
    }

    @Override
    // Este metodo tiene como objetivo cerrar la conexion con la base de datos
    public synchronized void cerrarConexion() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Conexión cerrada.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error al cerrar la conexión.");
        }
    }

    @Override
    // Este metodo tiene como objetivo buscar un juego en memoria usando el nombre
    // exacto, parcial, la primera palabra o el ID del juego (sincronizado con BD)
    public synchronized Juego buscarJuego(String fragmentoNombre) {
        obtenerJuegos(); // Refrescar memoria local desde la BD en tiempo real
        // Primero, intentar buscar por ID si el fragmento es numérico
        try {
            int idBuscado = Integer.parseInt(fragmentoNombre.trim());
            for (Juego juego : BD_juegos) {
                if (juego.getId() == idBuscado) {
                    return juego;
                }
            }
        } catch (NumberFormatException ignored) {
            // Si no es un número, proceder a buscar por nombre
        }

        for (Juego juego : BD_juegos) {
            if (juego.getNombre().toUpperCase().equals(fragmentoNombre.toUpperCase())) {
                return juego;
            }
        }

        for (Juego juego : BD_juegos) {
            if (juego.getNombre().toUpperCase().contains(fragmentoNombre.toUpperCase())) {
                return juego;
            }
        }

        for (Juego juego : BD_juegos) {
            String primeraPalabra = fragmentoNombre.split(" ")[0];
            String palabraLimpia = primeraPalabra.replaceAll("[^a-zA-Z0-9]", "");

            if (juego.getNombre().toUpperCase().contains(palabraLimpia.toUpperCase())) {
                return juego;
            }
        }

        System.out.println("No se encontró un juego que contenga: " + fragmentoNombre);
        return null;
    }

    @Override
    // Este metodo tiene como objetivo buscar la informacion de una moneda por su
    // codigo (sincronizado con BD)
    public synchronized Moneda buscarMoneda(String fragmentoCodigo) {
        ArrayList<Moneda> monedas = new ArrayList<>();
        String sql = "SELECT * FROM monedas";
        try (Statement query = connection.createStatement();
                ResultSet resultados = query.executeQuery(sql)) {
            while (resultados.next()) {
                String id = resultados.getString("codigo_moneda");
                double USDRatio = resultados.getDouble("tasa_conversion_a_usd");
                monedas.add(new Moneda(id, USDRatio));
            }
            BD_moneda = monedas;
        } catch (SQLException e) {
            System.err.println("Error al obtener monedas de la base de datos: " + e.getMessage());
        }
        for (Moneda moneda : BD_moneda) {
            if (moneda.getId().toUpperCase().equals(fragmentoCodigo.toUpperCase())) {
                return moneda;
            }
        }
        System.out.println("No se encontró una moneda que contenga: " + fragmentoCodigo);
        return null;
    }

    @Override
    // Este metodo tiene como objetivo buscar y eliminar un juego directamente de la
    // BD
    // y de la memoria por nombre parcial
    public synchronized boolean eliminarJuego(String fragmentoNombre) {
        Juego juego = buscarJuego(fragmentoNombre);
        if (juego != null) {
            String sql = "DELETE FROM juegos WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, juego.getId());
                ps.executeUpdate();
                BD_juegos.removeIf(j -> j.getId() == juego.getId());
                System.out.println("Juego eliminado de la BD y memoria: " + juego.getNombre());
                return true;
            } catch (SQLException e) {
                System.err.println("Error al eliminar juego de la BD: " + e.getMessage());
            }
        }
        System.out.println("No se encontró un juego para eliminar que contenga: " + fragmentoNombre);
        return false;
    }

    @Override
    // Este metodo tiene como objetivo calcular y retornar el equivalente en dolares
    // de un precio local
    public synchronized double convertirPrecioAUSD(double precioLocal, String moneda) {
        Moneda moneda_aux = buscarMoneda(moneda);
        if (moneda_aux != null) {
            double precioUSD = precioLocal * moneda_aux.getUSDRatio();
            precioUSD = Math.round(precioUSD * 100.0) / 100.0;
            return precioUSD;
        } else {
            return 0.0;
        }
    }

    // Este metodo tiene como objetivo establecer la conexion a MySQL y cargar los
    // datos iniciales a memoria
    public synchronized void conectarBD() {
        try {
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
                System.out.println("Conexión con la BD exitosa!");
            }

            Statement query = connection.createStatement();
            String sql = "SELECT * FROM juegos";
            ResultSet resultados = query.executeQuery(sql);
            BD_juegos.clear();

            int cont = 0;
            while (resultados.next()) {
                int id = resultados.getInt("id");
                String nombre = resultados.getString("nombre");
                BD_juegos.add(new Juego(nombre, id));

                cont += 1;
                System.out.printf(" || %-10s || %-65s", "cargado:", id + " - " + nombre);
                if (cont % 2 == 0)
                    System.out.println();
            }

            System.out.println("\nJUEGOS CARGADOS");

            Statement query2 = connection.createStatement();
            String sql2 = "SELECT * FROM paises";
            ResultSet resultados2 = query2.executeQuery(sql2);
            BD_paises.clear();

            cont = 0;
            while (resultados2.next()) {
                String id = resultados2.getString("codigo_pais");
                String nombre = resultados2.getString("nombre_pais");
                BD_paises.add(new Pais(nombre, id));

                cont += 1;
                System.out.printf(" || %-10s || %-65s", "cargado:", id + " - " + nombre);
                if (cont % 2 == 0)
                    System.out.println();
            }

            System.out.println("\nPAÍSES CARGADOS");

            Statement query3 = connection.createStatement();
            String sql3 = "SELECT * FROM monedas";
            ResultSet resultados3 = query3.executeQuery(sql3);
            BD_moneda.clear();

            cont = 0;
            while (resultados3.next()) {
                String id = resultados3.getString("codigo_moneda");
                double USDRatio = resultados3.getDouble("tasa_conversion_a_usd");
                BD_moneda.add(new Moneda(id, USDRatio));
                cont += 1;
                System.out.printf(" || %-10s || %-65s", "cargado:", id + " - " + USDRatio);
                if (cont % 2 == 0)
                    System.out.println();
            }
            System.out.println("\nMONEDAS CARGADAS");

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("No se pudo conectar a la BD");
        }
    }

    // Este metodo tiene como objetivo sincronizar los cambios locales en memoria
    // volcando la informacion hacia la base de datos
    public synchronized void actualizarBD() {
        try {
            connection.setAutoCommit(false);

            Statement stmt = connection.createStatement();
            stmt.executeUpdate("DELETE FROM juegos");
            stmt.executeUpdate("DELETE FROM monedas");

            // Insertar juegos
            String insertJuego = "INSERT INTO juegos (id, nombre) VALUES (?, ?)";
            PreparedStatement psJuego = connection.prepareStatement(insertJuego);
            for (Juego juego : BD_juegos) {
                psJuego.setInt(1, juego.getId());
                psJuego.setString(2, juego.getNombre());
                psJuego.executeUpdate();
            }

            String insertMoneda = "INSERT INTO monedas (codigo_moneda, tasa_conversion_a_usd) VALUES (?, ?)";
            PreparedStatement psMoneda = connection.prepareStatement(insertMoneda);
            for (Moneda moneda : BD_moneda) {
                psMoneda.setString(1, moneda.getId());
                psMoneda.setDouble(2, moneda.getUSDRatio());
                psMoneda.executeUpdate();
            }

            connection.commit();
            System.out.println("Base de datos actualizada correctamente con los datos actuales en memoria.");

        } catch (SQLException e) {
            e.printStackTrace();
            System.out.println("Error al actualizar la base de datos. Realizando rollback...");
            try {
                connection.rollback();
                System.out.println("Rollback ejecutado: la base de datos se mantiene en su estado anterior.");
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
                System.out.println("Error critico: no se pudo ejecutar el rollback.");
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    // --- MÉTODOS PARA RÉPLICA AUTÓNOMA DE STEAM (LÓGICA CONCURRENTE Y DISTRIBUIDA)
    // ---

    // Este método tiene como objetivo cifrar contraseñas usando hash MD5
    private String toMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : messageDigest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // Este método tiene como objetivo abrir una conexión independiente para evitar
    // conflictos de hilos
    private Connection createThreadConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    @Override
    // Este método tiene como objetivo validar las credenciales de inicio de sesión
    // de un usuario local
    public Usuario iniciarSesion(String username, String password) throws Exception {
        String sql = "SELECT * FROM usuarios WHERE nombre_usuario = ? AND contrasena = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, toMD5(password)); // Comparar contraseña con su hash MD5
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Usuario(
                            rs.getInt("id"),
                            rs.getString("nombre_usuario"),
                            rs.getString("correo"),
                            rs.getDouble("saldo_billetera"),
                            rs.getString("codigo_pais"));
                }
            }
        }
        return null;
    }

    @Override
    // Este método tiene como objetivo registrar una nueva cuenta de usuario en la
    // base de datos local
    public Usuario registrarUsuario(String username, String password, String email, String codigoPais) throws Exception {
        String insertSql = "INSERT INTO usuarios (nombre_usuario, contrasena, correo, saldo_billetera, codigo_pais) VALUES (?, ?, ?, 0.00, ?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, toMD5(password)); // Almacenar contraseña con su hash MD5
            ps.setString(3, email);
            ps.setString(4, codigoPais);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        return new Usuario(rs.getInt(1), username, email, 0.00, codigoPais);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error al registrar usuario en la BD: " + e.getMessage());
        }
        return null;
    }

    @Override
    // Este método tiene como objetivo realizar una compra segura mediante
    // transacciones atómicas distribuidas
    public boolean comprarJuego(int userId, int gameId, double precioUSD) throws Exception {
        // Ejecutar transacción de compra atómica con bloqueo a nivel de registro
        // (Row-Level Locking)
        try {
            connection.setAutoCommit(false);

            // 1. Verificar si ya posee el juego
            String checkSql = "SELECT 1 FROM bibliotecas WHERE id_usuario = ? AND id_juego = ?";
            try (PreparedStatement ps = connection.prepareStatement(checkSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, gameId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        throw new Exception("Ya posees este juego en tu biblioteca.");
                    }
                }
            }

            // 2. Verificar saldo con FOR UPDATE para bloquear la fila contra escrituras
            // simultaneas de otros servidores
            String balanceSql = "SELECT saldo_billetera FROM usuarios WHERE id = ? FOR UPDATE";
            double saldo = 0;
            try (PreparedStatement ps = connection.prepareStatement(balanceSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        saldo = rs.getDouble("saldo_billetera");
                    } else {
                        throw new Exception("Usuario no encontrado.");
                    }
                }
            }

            if (saldo < precioUSD) {
                throw new Exception(
                        "Saldo insuficiente en tu billetera de Steam (" + saldo + " USD vs " + precioUSD + " USD).");
            }

            // Descontar saldo de billetera
            String deductSql = "UPDATE usuarios SET saldo_billetera = saldo_billetera - ? WHERE id = ?";
            try (PreparedStatement ps = connection.prepareStatement(deductSql)) {
                ps.setDouble(1, precioUSD);
                ps.setInt(2, userId);
                ps.executeUpdate();
            }

            // Añadir juego a la biblioteca
            String libSql = "INSERT INTO bibliotecas (id_usuario, id_juego, minutos_jugados) VALUES (?, ?, 0)";
            try (PreparedStatement ps = connection.prepareStatement(libSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, gameId);
                ps.executeUpdate();
            }

            // Registrar compra
            String purchaseSql = "INSERT INTO compras (id_usuario, id_juego, monto_pagado) VALUES (?, ?, ?)";
            try (PreparedStatement ps = connection.prepareStatement(purchaseSql)) {
                ps.setInt(1, userId);
                ps.setInt(2, gameId);
                ps.setDouble(3, precioUSD);
                ps.executeUpdate();
            }

            connection.commit();
            return true;
        } catch (SQLException e) {
            connection.rollback();
            System.err.println("Error en transacción de compra: " + e.getMessage());
            throw e;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    @Override
    // Este método tiene como objetivo recargar dinero a la billetera virtual del
    // usuario de forma atómica
    public double recargarSaldo(int userId, double montoUSD) throws Exception {
        if (montoUSD <= 0) {
            throw new Exception("El monto a recargar debe ser mayor a 0.");
        }
        String sql = "UPDATE usuarios SET saldo_billetera = saldo_billetera + ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, montoUSD);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }

        String querySql = "SELECT saldo_billetera FROM usuarios WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(querySql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("saldo_billetera");
                }
            }
        }
        return 0.00;
    }

    @Override
    // Este método tiene como objetivo obtener todos los juegos comprados por un
    // usuario
    public synchronized ArrayList<Juego> obtenerBiblioteca(int userId) throws Exception {
        ArrayList<Juego> biblioteca = new ArrayList<>();
        String sql = "SELECT g.* FROM bibliotecas l JOIN juegos g ON l.id_juego = g.id WHERE l.id_usuario = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    biblioteca.add(new Juego(
                            rs.getString("nombre"),
                            rs.getInt("id")));
                }
            }
        }
        return biblioteca;
    }

    @Override
    // Este método tiene como objetivo calcular la intersección de juegos entre
    // varios perfiles locales en paralelo
    public ArrayList<Juego> obtenerJuegosEnComunLocal(ArrayList<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return new ArrayList<>();
        }

        int numThreads = Math.min(usernames.size(), 10);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        try {
            List<CompletableFuture<ArrayList<Juego>>> futures = usernames.stream()
                    .map(username -> CompletableFuture.supplyAsync(() -> {
                        ArrayList<Juego> userLibrary = new ArrayList<>();
                        String sql = "SELECT g.* FROM bibliotecas l "
                                + "JOIN usuarios u ON l.id_usuario = u.id "
                                + "JOIN juegos g ON l.id_juego = g.id "
                                + "WHERE u.nombre_usuario = ?";
                        // Creamos una conexión exclusiva por cada hilo concurrente para evitar bloqueos
                        try (Connection threadConn = createThreadConnection();
                                PreparedStatement ps = threadConn.prepareStatement(sql)) {
                            ps.setString(1, username);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                    userLibrary.add(new Juego(
                                            rs.getString("nombre"),
                                            rs.getInt("id")));
                                }
                            }
                        } catch (Exception e) {
                            System.err.println(
                                    "Error consultando biblioteca local de " + username + ": " + e.getMessage());
                        }
                        return userLibrary;
                    }, executor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ArrayList<Juego>> todasLasBibliotecas = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            if (todasLasBibliotecas.isEmpty())
                return new ArrayList<>();

            ArrayList<Juego> interseccion = new ArrayList<>(todasLasBibliotecas.get(0));

            for (int i = 1; i < todasLasBibliotecas.size(); i++) {
                java.util.HashSet<Integer> idsActuales = new java.util.HashSet<>();
                for (Juego j : todasLasBibliotecas.get(i)) {
                    idsActuales.add(j.getId());
                }
                interseccion.removeIf(juegoBase -> !idsActuales.contains(juegoBase.getId()));
            }

            return interseccion;
        } finally {
            executor.shutdown();
        }
    }

    // Retorna la moneda correspondiente a cada ID de país según la BD
    private String getCurrencyForCountry(String countryCode) {
        if (countryCode == null) return "USD";
        switch (countryCode.toLowerCase()) {
            case "us": return "USD";
            case "cl": return "CLP";
            case "in": return "INR";
            case "br": return "BRL";
            case "ca": return "CAD";
            case "cn": return "CNY";
            case "es": return "EUR";
            case "mx": return "MXN";
            case "pe": return "PEN";
            case "tr": return "TRY";
            case "au": return "AUD";
            default: return "USD";
        }
    }

    // Formatea el precio local según los estándares de Steam
    private String formatLocalPrice(double price, String currency) {
        switch (currency) {
            case "CLP": return String.format("CLP$ %,.0f", price).replace(',', '.');
            case "INR": return String.format("₹ %,.0f", price);
            case "EUR": return String.format("%,.2f€", price);
            case "USD": return String.format("USD $%,.2f", price);
            case "CAD": return String.format("CDN$ %,.2f", price);
            case "AUD": return String.format("A$ %,.2f", price);
            case "BRL": return String.format("R$ %,.2f", price).replace('.', ',');
            case "CNY": return String.format("¥ %,.2f", price);
            case "MXN": return String.format("Mex$ %,.2f", price);
            case "PEN": return String.format("S/.%,.2f", price);
            case "TRY": return String.format("$%,.2f USD", price);
            default: return String.format("%.2f %s", price, currency);
        }
    }
}
