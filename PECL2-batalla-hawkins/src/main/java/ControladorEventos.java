// Importación de la clase ThreadLocalRandom, que es más eficiente para generar números aleatorios en entornos multihilo
import java.util.concurrent.ThreadLocalRandom;

// La clase ControladorEventos hereda de Thread, lo que significa que se ejecutará como un hilo (proceso) independiente en segundo plano
public class ControladorEventos extends Thread {
    
    // Referencia al gestor principal del mundo, que controla el estado general de la simulación
    private final GestorMundo mundo;
    
    // Array que contiene los diferentes tipos de eventos globales que pueden ocurrir en el sistema
    private final String[] tiposEventos = {"APAGON", "TORMENTA", "ELEVEN", "RED_MENTAL"};

    // Constructor de la clase que recibe el gestor del mundo como parámetro para poder interactuar con él
    public ControladorEventos(GestorMundo mundo) {
        this.mundo = mundo;
    }

    // Método run(): es el corazón del hilo. Contiene el código que se ejecutará en paralelo cuando se inicie (start) el hilo
    @Override
    public void run() {
        try {
            // Bucle infinito que se ejecutará mientras el hilo no sea interrumpido externamente
            while (!Thread.currentThread().isInterrupted()) {
                
                // Verifica si la simulación está en pausa. Si es así, el hilo se detendrá aquí hasta que se reanude
                mundo.esperarSiPausado();
                
                // Espera de 30 a 60 segundos entre eventos
                // Genera un tiempo aleatorio en milisegundos (entre 30.000 y 60.000 ms)
                long tiempoHastaEvento = ThreadLocalRandom.current().nextLong(30000, 60001);
                // Pausa la ejecución de este hilo durante el tiempo generado (simulando la espera hasta el próximo evento)
                Thread.sleep(tiempoHastaEvento);
                
                // Verifica de nuevo si el programa fue pausado durante el tiempo que estuvo durmiendo (sleep)
                mundo.esperarSiPausado();
                
                // Seleccionar evento y duración
                // Elige un evento aleatorio del array usando un índice aleatorio entre 0 y la longitud del array
                String tipo = tiposEventos[ThreadLocalRandom.current().nextInt(tiposEventos.length)];
                // Genera una duración aleatoria para el evento que va de 5 a 10 segundos (5.000 a 10.000 ms)
                long duracionEvento = ThreadLocalRandom.current().nextLong(5000, 10001);
               
                // Notifica al gestor del mundo que un nuevo evento ha comenzado, pasándole el tipo y su duración
                mundo.iniciarEvento(tipo, duracionEvento);
                
                // Pausa este hilo durante el tiempo que dura el evento (para simular que el evento está ocurriendo)
                Thread.sleep(duracionEvento);

                // Finalizar el evento
                // Una vez transcurrido el tiempo del evento, notifica al gestor que el evento ha terminado
                mundo.finalizarEvento(tipo);
            }
        } catch (InterruptedException e) {
            // Si el hilo es interrumpido (por ejemplo, al cerrar el programa), se captura la excepción y se imprime un mensaje
            System.out.println("Controlador de eventos finalizado.");
        }
    }
}