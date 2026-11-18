package federacion.monitores;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// La clase GestorLog actúa como un Monitor clásico. Es el guardián absoluto del archivo físico
// y de la consola. Como decenas de hilos (delegados, patrullas, saqueadores) van a intentar 
// escribir sus hazañas exactamente al mismo tiempo, este monitor asegura que lo hagan en orden.
public class GestorLog {

    // Uso del patrón de diseño 'Observer' (Observador). Esta variable guarda una referencia a un método
    // de la interfaz gráfica sin necesidad de conocer cómo funciona la interfaz por dentro.
    // Permite que el backend (los hilos) le pase texto al frontend (la pantalla) de forma desacoplada.
    private java.util.function.Consumer<String> observadorUi;
    
    // Ruta física donde se guardará el registro inmutable de la simulación
    private final String rutaArchivo;
    // Herramienta para estandarizar las marcas de tiempo (timestamps) de cada evento
    private final DateTimeFormatter formateador;

    // Constructor que se ejecuta de forma estrictamente secuencial desde el Main antes de que nazca ningún hilo
    public GestorLog() {
        this.rutaArchivo = "federacion_galactica.txt";
        this.formateador = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // Prepara el archivo físico para una nueva partida
        inicializarArchivo();
    }

    // Método privado que limpia el archivo al inicio de cada simulación.
    // No necesita ser 'synchronized' porque se llama desde el constructor en el hilo principal (Main Thread),
    // cuando la concurrencia aún no ha empezado.
    private void inicializarArchivo() {
        // Al pasar 'false' al FileWriter, le decimos que sobrescriba el archivo si ya existía,
        // borrando el historial de la simulación anterior para empezar en blanco.
        try (PrintWriter writer = new PrintWriter(new FileWriter(rutaArchivo, false))) {
            writer.println("Inicio de la simulacion de la federacion galactica ");
        } catch (IOException e) {
            System.err.println("Error al inicializar el log: " + e.getMessage());
        }
    }
    
    // Método llamado desde el Main para "conectar" la interfaz gráfica con este monitor
    public void setObservadorUi(java.util.function.Consumer<String> observador) {
        this.observadorUi = observador;
    }

    // ¡Punto crítico de concurrencia! Al ser 'synchronized', este método actúa como un embudo estricto.
    // Si un Saqueador y un Delegado intentan registrar un evento en el mismo milisegundo, uno de los hilos
    // adquirirá el cerrojo primero y el otro quedará en estado 'Blocked' esperando a que el primero termine de escribir.
    public synchronized void registrarEvento(String mensaje) {
        // Genera la marca de tiempo exacta del momento en que el hilo logró entrar al monitor
        String fechaHora = LocalDateTime.now().format(formateador);
        String linea = String.format("[%s] %s", fechaHora, mensaje);
        
        // Imprime en la consola de salida estándar (útil para depuración)
        System.out.println(linea);
        
        // Escritura atómica en el fichero físico.
        // Al pasar 'true' al FileWriter, le indicamos que añada el texto al final del archivo (modo Append),
        // protegiendo lo que los hilos anteriores ya habían escrito.
        try (PrintWriter writer = new PrintWriter(new FileWriter(rutaArchivo, true))) {
            writer.println(linea);
        } catch (IOException e) {
            System.err.println("Error escribiendo en el log: " + e.getMessage());
        }

        // Si la interfaz gráfica ya ha sido conectada...
        if (observadorUi != null) {
            // ...se le envía el texto. 
            // Regla de oro de Java Swing: NUNCA actualizar la interfaz gráfica desde un hilo de trabajo (worker thread).
            // EventQueue.invokeLater() empaqueta la orden de pintar el texto y se la envía al 
            // Event Dispatch Thread (EDT), que es el único hilo con permiso para tocar la pantalla sin colgar el programa.
            java.awt.EventQueue.invokeLater(() -> observadorUi.accept(linea));
        }
    }
}