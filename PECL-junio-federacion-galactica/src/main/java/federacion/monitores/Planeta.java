package federacion.monitores;

import federacion.modelo.TipoRecurso;
import java.util.ArrayList;
import java.util.List;

// La clase Planeta actúa como un "Monitor" clásico en concurrencia.
// Es un entorno de memoria compartida donde los hilos deben entrar de uno en uno (usando el cerrojo 'synchronized')
// para leer o modificar las variables sin pisarse los unos a los otros.
public class Planeta {

    // Características inmutables de este planeta
    private final String nombre;
    private final TipoRecurso recursoGenerado;
    private final GestorLog log;

    // Control de Delegados (Aforo máximo de naves mineras)
    private final int MAX_DELEGADOS = 4;
    private int delegadosPresentes = 0;
    
    // Cola física (FIFO) para garantizar que los delegados entren por estricto orden de llegada
    private final List<Thread> colaDelegados = new ArrayList<>();
    // Registro exacto de qué delegados están extrayendo material en este preciso instante.
    // Vital para que el saqueador sepa a quién tiene que robar y expulsar.
    private final List<Thread> hilosDelegados;

    // Control de Patrullas (Aforo máximo de naves defensivas)
    private final int MAX_PATRULLAS = 3;
    private int patrullasPresentes = 0;
    
    // Fila física para mantener el orden de llegada de las patrullas y registro de las que están vigilando
    private final List<Thread> colaPatrullas = new ArrayList<>();
    private final List<Thread> hilosPatrullas = new ArrayList<>();

    // Bandera de estado de emergencia. Cuando un saqueador asalta el planeta, se pone a true.
    // Esto funciona como una cuarentena: nadie puede entrar, y los que están dentro no pueden salir.
    private boolean bajoAtaque = false;

    // Constructor que inicializa el planeta y prepara sus listas de control
    public Planeta(String nombre, TipoRecurso recursoGenerado, GestorLog log) {
        this.nombre = nombre;
        this.recursoGenerado = recursoGenerado;
        this.log = log;
        this.hilosDelegados = new ArrayList<>();
    }

    // Getters sincronizados. Al añadir 'synchronized', obligamos a la interfaz gráfica a pedir el cerrojo
    // antes de leer. Así evitamos que la pantalla muestre datos corruptos si lee justo cuando un hilo está modificándolos.
    public String getNombre() { return nombre; }
    public TipoRecurso getRecursoGenerado() { return recursoGenerado; }
    public synchronized int getDelegadosPresentes() { return delegadosPresentes; }
    public synchronized int getPatrullasPresentes() { return patrullasPresentes; }
    public synchronized boolean isBajoAtaque() { return bajoAtaque; }
    
    public synchronized int getDelegadosEnCola() { return colaDelegados.size(); }

    // ==========================================
    // ACCESO DE DELEGADOS (Zona de Extracción)
    // ==========================================
    public synchronized void entrarDelegado(String idDelegado) throws InterruptedException {
        // Nada más intentar entrar, el delegado se apunta al final de la cola
        colaDelegados.add(Thread.currentThread()); 

        try {
            // EVALUACIÓN DE BLOQUEO. El delegado se dormirá (wait) liberando el cerrojo si:
            // 1. El planeta ya tiene 4 delegados (aforo completo).
            // 2. Hay un saqueador atacando.
            // 3. No es el primero de la cola (hay otros delegados que llegaron antes que él).
            while (delegadosPresentes >= MAX_DELEGADOS || bajoAtaque || colaDelegados.get(0) != Thread.currentThread()) {
                wait();
            }

            // Si pasa la barrera del wait(), es su turno. Se borra de la cola y ocupa su plaza en el planeta.
            colaDelegados.remove(Thread.currentThread()); 
            delegadosPresentes++;
            hilosDelegados.add(Thread.currentThread());
            log.registrarEvento("El delegado " + idDelegado + " entra en el planeta " + nombre + ".");
            // Al quitarse de la cola, avisa al resto para que el siguiente evalúe si ya puede entrar
            notifyAll(); 
            
        } catch (InterruptedException e) {
            // Si el delegado estaba haciendo cola tranquilamente y un saqueador ataca, el pánico le hace huir.
            // Se borra de la cola para no atascarla y avisa a los demás antes de irse.
            colaDelegados.remove(Thread.currentThread());
            notifyAll(); 
            throw e; 
        }
    }

    // Se llama cuando el delegado ha terminado de extraer y se marcha
    public synchronized void salirDelegado(String idDelegado, boolean fueExpulsado) {
        // Siempre libera su espacio físico en el planeta
        delegadosPresentes--;
        hilosDelegados.remove(Thread.currentThread());
        
        // Si sale por su propio pie (no fue asaltado por un pirata), se registra su éxito
        if (!fueExpulsado) {
            log.registrarEvento("El delegado " + idDelegado + " finaliza extracción y sale de " + nombre + ".");
        }
        // Despierta a los que esperan en la cola de fuera para que el siguiente pueda entrar a trabajar
        notifyAll(); 
    }

    // ==========================================
    // ACCESO DE PATRULLAS (Zona de Defensa)
    // ==========================================
    public synchronized void entrarPatrulla(String idPatrulla) throws InterruptedException {
        // La patrulla se apunta en su cola exclusiva
        colaPatrullas.add(Thread.currentThread()); 
        
        try {
            // Espera su turno si la órbita está llena, bajo asedio, o hay otras patrullas delante
            while (patrullasPresentes >= MAX_PATRULLAS || bajoAtaque || colaPatrullas.get(0) != Thread.currentThread()) {
                wait();
            }
            
            // Inicia su guardia
            colaPatrullas.remove(Thread.currentThread());
            patrullasPresentes++;
            hilosPatrullas.add(Thread.currentThread()); 
            log.registrarEvento("La patrulla " + idPatrulla + " llega a " + nombre + ".");
            notifyAll();
            
        } catch (InterruptedException e) {
            // Si huye mientras hacía cola, se limpia para mantener la coherencia de la lista
            colaPatrullas.remove(Thread.currentThread());
            notifyAll();
            throw e;
        }
    }

    public synchronized void salirPatrulla(String idPatrulla) throws InterruptedException {
        // MECANISMO HEROICO: Si una patrulla ha terminado su ronda y quiere irse, pero en ese
        // preciso instante el planeta está siendo atacado, no huirá. Se quedará suspendida (wait)
        // bloqueando su salida para defender la zona mientras dure el asedio.
        while (bajoAtaque) {
            wait(); 
        }
        
        // ESCUDO DE SEGURIDAD: Solo resta su presencia si el sistema confirma que estaba dentro.
        // Esto evita contadores negativos si un hilo se comporta de forma anómala.
        if (hilosPatrullas.contains(Thread.currentThread())) {
            patrullasPresentes--;
            hilosPatrullas.remove(Thread.currentThread());
            log.registrarEvento("La patrulla " + idPatrulla + " abandona " + nombre + ".");
            notifyAll();
        }
    }
    
    // Método de emergencia invocado desde la clase Patrulla cuando es derribada en combate
    public synchronized void bajaPorDerrotaPatrulla() {
        // Borra a la nave destruida de los registros del planeta de forma silenciosa y segura
        if (hilosPatrullas.contains(Thread.currentThread())) {
            patrullasPresentes--;
            hilosPatrullas.remove(Thread.currentThread());
            notifyAll();
        }
    }

    // ==========================================
    // ATAQUES DE SAQUEADORES (Eventos Hostiles)
    // ==========================================
    
    // 1. El saqueador llega e impone un bloqueo total en el planeta
    public synchronized void bloquearZona(String idSaqueador) {
        bajoAtaque = true; // Sube la bandera roja. A partir de ahora los 'wait()' actuarán como jaulas.
        log.registrarEvento("El saqueador " + idSaqueador + " llega a " + nombre + " y bloquea la zona.");
        notifyAll(); // Despierta a todos para que lean el nuevo estado de peligro
    }

    // 2. Consecuencias del saqueo (Solo ocurre si el pirata eliminó a las patrullas previamente)
    public synchronized void ejecutarSaqueo(String idSaqueador) {
        log.registrarEvento("El saqueador " + idSaqueador + " inicia la fase de saqueo en " + nombre + ".");
        // Busca a todos los delegados que estaban extrayendo pacíficamente
        for (Thread hilo : hilosDelegados) {
            // Dispara una orden de interrupción al hilo del delegado. 
            // Esto romperá la espera del delegado en su clase, saltando al catch y huyendo al hospital.
            hilo.interrupt(); 
        }
        notifyAll();
    }

    // 3. Ejecución del daño militar (Llamado durante el duelo a muerte)
    public synchronized void expulsarPatrullaEnCombate() {
        if (!hilosPatrullas.isEmpty()) {
            // Fija como objetivo a la primera patrulla que estaba defendiendo
            Thread patrullaDerrotada = hilosPatrullas.get(0);
            // Rompe su 'wait()' heroico de forma forzosa, simulando la destrucción de la nave
            patrullaDerrotada.interrupt();
        }
    }

    // 4. Retirada del pirata
    public synchronized void finalizarAtaque() {
        bajoAtaque = false; // Baja la bandera roja. El planeta vuelve a ser seguro.
        log.registrarEvento("El ataque en " + nombre + " ha finalizado. Zona libre.");
        // Despierta a todas las naves. Las patrullas atrapadas ahora podrán irse, y la cola empezará a avanzar.
        notifyAll();
    }
}