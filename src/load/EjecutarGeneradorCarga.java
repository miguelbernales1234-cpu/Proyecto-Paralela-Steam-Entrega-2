package load;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Punto de entrada para ejecutar la prueba de carga concurrente.
 *
 * Uso:
 *   java load.EjecutarGeneradorCarga [host] [puerto] [duracionSegundos] [cantidadHilos]
 *
 * Valores por defecto:
 *   host             = localhost
 *   puerto           = 5000  (nodo 1)
 *   duracionSegundos = 60    (mínimo requerido por la pauta)
 *   cantidadHilos    = 50    (mínimo requerido por la pauta)
 *
 * El reporte se imprime en consola Y se guarda en logs/load_test_<timestamp>.log
 */
public class EjecutarGeneradorCarga {

    public static void main(String[] args) throws Exception {
        // Parámetros configurables por línea de comando
        String host          = args.length > 0 ? args[0] : "localhost";
        int    puerto        = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        long   duracion      = args.length > 2 ? Long.parseLong(args[2])   : 60;
        int    cantidadHilos = args.length > 3 ? Integer.parseInt(args[3]) : 50;

        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║      PRUEBA DE CARGA — Sistema Distribuido Steam         ║");
        System.out.println("╠══════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Nodo destino : %s:%-5d                              ║%n", host, puerto);
        System.out.printf( "║  Hilos        : %-5d                                    ║%n", cantidadHilos);
        System.out.printf( "║  Duración     : %-5d segundos                           ║%n", duracion);
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.println();

        // Crear directorio de logs si no existe
        new File("logs").mkdirs();

        // Redirigir System.out a archivo de log además de consola
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String archivoLog = "logs/load_test_" + timestamp + ".log";

        // Ejecutar generador de carga
        GeneradorCarga generador = new GeneradorCarga(host, puerto, cantidadHilos, duracion);

        // Capturar la salida para guardarla en el log
        PrintStreamDuplicado logStream = new PrintStreamDuplicado(new FileWriter(archivoLog, true), true);
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            private final java.io.PrintStream consola = new java.io.PrintStream(
                    new java.io.FileOutputStream(java.io.FileDescriptor.out));
            @Override
            public void write(int b) {
                consola.write(b);
                logStream.write(b);
            }
            @Override
            public void write(byte[] b, int off, int len) {
                consola.write(b, off, len);
                logStream.write(b, off, len);
            }
        }));

        generador.ejecutar();

        System.out.println();
        System.out.println("[EjecutarCarga] Log guardado en: " + new File(archivoLog).getAbsolutePath());

        logStream.close();
    }

    // Este metodo tiene como objetivo crear un PrintStream que escribe tanto a consola como a archivo
    static class PrintStreamDuplicado extends java.io.PrintStream {
        PrintStreamDuplicado(FileWriter fw, boolean autoFlush) {
            super(new java.io.OutputStream() {
                private final FileWriter escritorArchivo = fw;
                @Override
                public void write(int b) throws java.io.IOException {
                    escritorArchivo.write(b);
                    escritorArchivo.flush();
                }
                @Override
                public void write(byte[] b, int off, int len) throws java.io.IOException {
                    escritorArchivo.write(new String(b, off, len));
                    escritorArchivo.flush();
                }
            }, autoFlush);
        }
    }
}
