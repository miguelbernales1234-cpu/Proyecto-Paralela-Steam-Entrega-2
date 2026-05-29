package common;

import java.io.Serializable;

/**
 * Representa el precio de un juego en una región específica de Steam.
 * Almacena tanto el precio en moneda local (como lo muestra Steam) como
 * su equivalente convertido a USD para facilitar la comparación entre regiones.
 */
public class PrecioRegional implements Serializable {
    private static final long serialVersionUID = 1L;

    private String paisNombre;
    private String monedaLocal;
    private double precioLocal;
    private String precioFormateado; // Ej: "CLP$ 14.999", "$ 29.99"
    private double precioUSD;

    // Este metodo tiene como objetivo inicializar un precio regional con todos sus datos
    public PrecioRegional(String paisNombre, String monedaLocal, double precioLocal,
            String precioFormateado, double precioUSD) {
        this.paisNombre = paisNombre;
        this.monedaLocal = monedaLocal;
        this.precioLocal = precioLocal;
        this.precioFormateado = precioFormateado;
        this.precioUSD = precioUSD;
    }

    // Este metodo tiene como objetivo retornar el nombre del pais
    public String getPaisNombre() {
        return paisNombre;
    }

    // Este metodo tiene como objetivo retornar el codigo de moneda local (ej: "CLP", "USD", "BRL")
    public String getMonedaLocal() {
        return monedaLocal;
    }

    // Este metodo tiene como objetivo retornar el precio en moneda local
    public double getPrecioLocal() {
        return precioLocal;
    }

    // Este metodo tiene como objetivo retornar el precio formateado como lo muestra Steam (ej: "CLP$ 14.999")
    public String getPrecioFormateado() {
        return precioFormateado;
    }

    // Este metodo tiene como objetivo retornar el precio convertido a USD
    public double getPrecioUSD() {
        return precioUSD;
    }
}
