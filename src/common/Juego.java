package common;

import java.io.Serializable;

public class Juego implements Serializable {
    private static final long serialVersionUID = 2L;

    private String nombre;
    private int id;
    /**
     * Tipo de ítem en Steam: "game", "dlc", "demo", "mod", "soundtrack", etc.
     * Permite distinguir si el App ID corresponde a un juego jugable o a otro tipo de contenido.
     */
    private String tipo;

    // Este metodo tiene como objetivo inicializar una instancia de Juego con su nombre e id
    public Juego(String nombre, int id) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = "game"; // valor por defecto
    }

    // Este metodo tiene como objetivo inicializar una instancia de Juego con nombre, id y tipo
    public Juego(String nombre, int id, String tipo) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = (tipo != null && !tipo.isBlank()) ? tipo : "game";
    }

    // Este metodo tiene como objetivo retornar el id del juego
    public int getId() {
        return id;
    }

    // Este metodo tiene como objetivo retornar el nombre del juego
    public String getNombre() {
        return nombre;
    }

    // Este metodo tiene como objetivo retornar el tipo de item en Steam (game, dlc, demo, etc.)
    public String getTipo() {
        return tipo;
    }

    // Este metodo tiene como objetivo establecer el id del juego
    public void setId(int id) {
        this.id = id;
    }

    // Este metodo tiene como objetivo establecer el nombre del juego
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    // Este metodo tiene como objetivo establecer el tipo de item en Steam
    public void setTipo(String tipo) {
        this.tipo = tipo;
    }
}
