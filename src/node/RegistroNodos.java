package node;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de todos los nodos del cluster.
 * Lee la configuración desde el archivo nodes.txt con el formato:
 *   idNodo,host,puertoCliente,puertoPeer
 * Ejemplo:
 *   1,localhost,5000,6000
 *   2,localhost,5001,6001
 *   3,localhost,5002,6002
 */
public class RegistroNodos {

    private final List<InfoNodo> nodos = new ArrayList<>();

    // Este metodo tiene como objetivo cargar la lista de nodos desde el archivo de configuracion
    public RegistroNodos(String rutaArchivoConfig) {
        cargarDesdeArchivo(rutaArchivoConfig);
    }

    // Este metodo tiene como objetivo cargar y parsear la lista de nodos del archivo nodes.txt
    private void cargarDesdeArchivo(String ruta) {
        try (BufferedReader br = new BufferedReader(new FileReader(ruta))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("#")) continue;
                String[] partes = linea.split(",");
                if (partes.length < 4) continue;
                int    id            = Integer.parseInt(partes[0].trim());
                String host          = partes[1].trim();
                int    puertoCliente = Integer.parseInt(partes[2].trim());
                int    puertoPeer    = Integer.parseInt(partes[3].trim());
                nodos.add(new InfoNodo(id, host, puertoCliente, puertoPeer));
            }
            System.out.println("[RegistroNodos] Nodos cargados: " + nodos.size());
        } catch (IOException e) {
            System.err.println("[RegistroNodos] ERROR leyendo " + ruta + ": " + e.getMessage());
        }
    }

    // Este metodo tiene como objetivo retornar todos los nodos del cluster
    public List<InfoNodo> obtenerTodosLosNodos() {
        return nodos;
    }

    // Este metodo tiene como objetivo buscar la informacion de un nodo por su ID
    public InfoNodo obtenerNodo(int idNodo) {
        for (InfoNodo n : nodos) {
            if (n.obtenerIdNodo() == idNodo) return n;
        }
        return null;
    }

    // Este metodo tiene como objetivo retornar los nodos con ID mayor al indicado (para algoritmo Bully)
    public List<InfoNodo> obtenerNodosConIdMayor(int miId) {
        List<InfoNodo> resultado = new ArrayList<>();
        for (InfoNodo n : nodos) {
            if (n.obtenerIdNodo() > miId) resultado.add(n);
        }
        return resultado;
    }

    // Este metodo tiene como objetivo retornar los nodos distintos al indicado (peers)
    public List<InfoNodo> obtenerPares(int miId) {
        List<InfoNodo> resultado = new ArrayList<>();
        for (InfoNodo n : nodos) {
            if (n.obtenerIdNodo() != miId) resultado.add(n);
        }
        return resultado;
    }
}
