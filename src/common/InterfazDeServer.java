package common;

import java.util.ArrayList;

public interface InterfazDeServer {
    // Este metodo tiene como objetivo cerrar la conexion con el servidor
    public void cerrarConexion();
    // Este metodo tiene como objetivo obtener la lista de juegos disponibles
    public ArrayList<Juego> obtenerJuegos();

    // Este metodo tiene como objetivo eliminar un juego segun su nombre
    public boolean eliminarJuego(String nombre);
    // Este metodo tiene como objetivo buscar un juego especifico por su nombre
    public Juego buscarJuego(String nombre);
    // Este metodo tiene como objetivo obtener el precio de un juego desde la API de Steam para un pais determinado
    public double getPriceFromApiSteam(int id_juego, String id_pais);
    // Este metodo tiene como objetivo convertir un precio local a dolares (USD) usando su moneda
    public double convertirPrecioAUSD(double precioLocal, String moneda);
    // Este metodo tiene como objetivo buscar una moneda por su ID
    public Moneda buscarMoneda(String id);

    // Este metodo tiene como objetivo obtener los precios de un juego en multiples paises
    public ArrayList<Double> getPricesFromMultipleCountries(int id_juego, ArrayList<String> id_paises);
    // Este metodo tiene como objetivo obtener los precios de un juego en multiples paises con moneda local incluida
    public ArrayList<PrecioRegional> getPreciosRegionales(int id_juego, ArrayList<Pais> paises);
    // Este metodo tiene como objetivo obtener los juegos en comun para una lista de usuarios de Steam
    public ArrayList<Juego> obtenerJuegosEnComun(ArrayList<String> steamIds);
    // Este metodo tiene como objetivo obtener la lista de paises soportados
    public ArrayList<Pais> obtenerPaises();
    
    // Métodos para réplica autónoma de Steam:
    public Usuario iniciarSesion(String username, String password) throws Exception;
    public Usuario registrarUsuario(String username, String password, String email) throws Exception;
    public boolean comprarJuego(int userId, int gameId, double precioUSD) throws Exception;

    public double recargarSaldo(int userId, double montoUSD) throws Exception;
    public ArrayList<Juego> obtenerJuegosEnComunLocal(ArrayList<String> usernames);
    public ArrayList<Juego> obtenerBiblioteca(int userId) throws Exception;
}
