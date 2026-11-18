// Importación de la clase ThreadLocalRandom para generar números aleatorios de forma eficiente en entornos multihilo
import java.util.concurrent.ThreadLocalRandom;

// La clase Demogorgon hereda de Thread, simulando el comportamiento de un monstruo de forma concurrente (independiente)
public class Demogorgon extends Thread {
    
    // Identificador único para cada Demogorgon
    private final String id;
    // Referencia al gestor principal del mundo para poder interactuar con él y conocer el estado global
    private final GestorMundo mundo;
    // Contador interno para registrar a cuántos niños ha capturado este Demogorgon en particular
    private int capturasIndividuales = 0;

    // Constructor que inicializa la identidad del Demogorgon y el mundo en el que habita
    public Demogorgon(String id, GestorMundo mundo) {
        this.id = id;
        this.mundo = mundo;
    }

    // Método run() que contiene el ciclo de vida principal de este hilo (Demogorgon)
    @Override
    public void run() {
        try {
            // Variable para recordar en qué zona se encuentra el Demogorgon actualmente
            String zonaActual = "";
            
            // Bucle principal: el Demogorgon actuará indefinidamente hasta que el hilo sea interrumpido
            while (!Thread.currentThread().isInterrupted()) {
                
                // Comprueba si la simulación principal está pausada. Si es así, se detiene aquí.
                mundo.esperarSiPausado();
                // Se paralizan si Eleven interviene en el mundo (evento especial)
                mundo.esperarSiEleven();

                // El Demogorgon se mueve a una nueva zona aleatoria del "Upside Down", diferente a la actual
                zonaActual = mundo.obtenerZonaUpsideDownAleatoria(zonaActual);
                // Notifica al mundo que este Demogorgon ha entrado físicamente a dicha zona
                mundo.entrarZonaDemogorgon(this, zonaActual);

                // Intenta encontrar un niño (Nino) en la zona actual para atacarlo
                Nino objetivo = mundo.seleccionarObjetivoEnZona(zonaActual);

                // Si hay un objetivo en la zona y el objetivo está disponible para ser atacado
                if (objetivo != null && objetivo.intentarSerAtacado()) {
                    
                    // Verificaciones de pausa antes de iniciar el ataque real
                    mundo.esperarSiPausado();
                    mundo.esperarSiEleven(); 

                    // Calcula una duración aleatoria para el forcejeo/ataque (entre 500 y 1500 milisegundos)
                    long duracionAtaque = ThreadLocalRandom.current().nextLong(500, 1501);
                    // Pausa el hilo para simular el tiempo que dura el ataque
                    Thread.sleep(duracionAtaque); 
                    // Verifica de nuevo si se pausó durante el ataque
                    mundo.esperarSiPausado();

                    // Se le pregunta al gestor del mundo si el ataque tuvo éxito o el niño logró escapar
                    boolean exito = mundo.resolverAtaque(this, objetivo); 

                    // Si el ataque fue exitoso (el Demogorgon capturó al niño)
                    if (exito) {
                        // Calcula un tiempo extra para simular el proceso de "depositar" o llevarse a la presa
                        long tiempoDeposito = ThreadLocalRandom.current().nextLong(500, 1001);
                        Thread.sleep(tiempoDeposito); // Simula ese tiempo de depósito
                        
                        mundo.esperarSiPausado();
                        // Incrementa el contador personal de capturas del Demogorgon
                        capturasIndividuales++;
                        // Notifica al mundo la captura oficial para que actualice las estadísticas y el estado del niño
                        mundo.registrarCaptura(this, objetivo);
                        // Libera el estado de "siendo atacado" del niño, indicando que el resultado final es true (fue capturado)
                        objetivo.finAtaque(true);
                    } else {
                        // Si el ataque falló (el niño escapó), simplemente libera el estado del niño indicando false (no capturado)
                        objetivo.finAtaque(false);
                    }
                } else {
                    // Si no encontró objetivo en la zona, el Demogorgon debe esperar antes de volver a buscar
                    long tiempoEspera = ThreadLocalRandom.current().nextLong(4000, 5001);
                    
                    // tormenta: Demogorgon más agresivo, tiempo entre ataques a la mitad
                    // Si el evento de "Tormenta" está activo, la espera se reduce, haciéndolo más rápido y peligroso
                    if (mundo.isTormentaActiva()) {
                        tiempoEspera /= 2;
                    }
                    
                    // El Demogorgon descansa/espera este tiempo antes de moverse a otra zona
                    Thread.sleep(tiempoEspera);
                    
                    // Revisa pausas al despertar
                    mundo.esperarSiPausado();
                }
                // Al terminar su turno (haya atacado o no), notifica al mundo que sale de la zona actual para ir a otra en la siguiente iteración
                mundo.salirZonaDemogorgon(this, zonaActual);
            }
        } catch (InterruptedException e) {
            // Si el programa detiene forzosamente el hilo (ej. al cerrar la aplicación), se captura la excepción
            System.out.println("Demogorgon " + id + " finalizado.");
        }
    }

    // Método "getter" para devolver el identificador del Demogorgon
    public String getIdDemogorgon() { return id; }
    
    // Método "getter" para devolver cuántas capturas lleva este Demogorgon (útil para el ranking)
    public int getCapturas() { return capturasIndividuales; }
}