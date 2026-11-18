package federacion.hilos;

import federacion.modelo.TipoRecurso;
import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
import java.util.List;
import java.util.Random;

// Al heredar de Thread, cada instancia de esta clase representa un hilo de ejecución independiente
// cuyo ciclo de vida corre en paralelo al resto del sistema tras invocar el método start().
public class Delegado extends Thread { 

    private final String id;
    private final SistemaCentral sistemaCentral;
    private final List<Planeta> planetas;
    private final List<Deposito> depositos;
    private final GestorLog log;
    private final Random rand;
    
    // Bandera de estado leída concurrentemente por el hilo GeneradorActores.
    // Permite controlar de forma atómica si este hilo cuenta para el cupo máximo de 20 activos en el sistema.
    private boolean activo = true; 

    // Constructor que inyecta los monitores compartidos y registra el nombre del hilo en la JVM (super(id))
    public Delegado(String id, SistemaCentral sistemaCentral, List<Planeta> planetas, List<Deposito> depositos, GestorLog log) {
        super(id); 
        this.id = id;
        this.sistemaCentral = sistemaCentral;
        this.planetas = planetas;
        this.depositos = depositos;
        this.log = log;
        this.rand = new Random();
    }

    public boolean isActivo() {
        return activo;
    }

    // Método principal del hilo. Todo el código interno es ejecutado de forma asíncrona por el planificador del Sistema Operativo.
    @Override
    public void run() { 
        while (true) {
            // Bloque general de protección para atrapar interrupciones "fantasma" que puedan ocurrir
            // mientras el hilo se encuentra bloqueado (wait) en la cola de alguno de los monitores.
            try { 
                // Llamada a un método 'synchronized'. El hilo adquiere el cerrojo (lock) intrínseco del monitor para registrarse de forma segura.
                sistemaCentral.entrarCentroCoordinacion(id); 
                // Transiciona al estado 'Timed Waiting'. El hilo cede el uso de la CPU durante este tiempo, pero NO liberaría cerrojos si los tuviera en este momento.
                Thread.sleep(rand.nextInt(2000) + 2000); 
                sistemaCentral.salirCentroCoordinacion(id); 

                TipoRecurso recursoDeseado = TipoRecurso.values()[rand.nextInt(TipoRecurso.values().length)];
                Planeta planetaDestino = seleccionarPlaneta(recursoDeseado);

                // Llamada bloqueante a la región crítica. Si el planeta ha alcanzado su capacidad máxima o está bajo ataque,
                // el hilo invocará wait() internamente, suspendiendo su ejecución y liberando el cerrojo hasta recibir un notifyAll().
                planetaDestino.entrarDelegado(id); 
                boolean fueExpulsadoDePlaneta = false;
                int cantidadExtraida = 0;
                
                // Periodo de vulnerabilidad. Mientras el hilo simula la extracción de recursos (durmiendo),
                // es susceptible de recibir la señal interrupt() proveniente del hilo Saqueador si este gana un combate.
                try { 
                    Thread.sleep(rand.nextInt(2000) + 3000);   
                    cantidadExtraida = rand.nextInt(16) + 10; 
                // Captura la interrupción lanzada abruptamente por el Saqueador, sacando al hilo de su estado de suspensión.
                } catch (InterruptedException e) { 
                    fueExpulsadoDePlaneta = true; 
                // Cláusula vital para la prevención de interbloqueos (Deadlocks). Garantiza que el hilo actualice
                // las variables del monitor y despierte a otros hilos (notifyAll) sin importar si terminó con éxito o por una excepción.
                } finally { 
                    planetaDestino.salirDelegado(id, fueExpulsadoDePlaneta); 
                }

                if (fueExpulsadoDePlaneta) {
                    irAZonaRecuperacion(10000, 15000);
                    // Rompe el flujo de ejecución secuencial y fuerza una nueva iteración del bucle, esquivando la fase de depósito.
                    continue; 
                }

                Deposito depositoDestino = seleccionarDeposito(recursoDeseado); 
                
                // Nuevo bloqueo potencial. El hilo debe esperar su turno en la cola estricta (FIFO) y comprobar 
                // que existe capacidad aritmética libre (espacioReservado) antes de poder acceder al monitor del depósito.
                depositoDestino.entrarADepositar(id, cantidadExtraida); 
                boolean fueExpulsadoDeDeposito = false;

                try {
                    Thread.sleep(rand.nextInt(1000) + 2000); 
                } catch (InterruptedException e) {
                    fueExpulsadoDeDeposito = true; 
                } finally {
                    depositoDestino.salirDeDepositar(id, cantidadExtraida, fueExpulsadoDeDeposito); 
                }

                if (fueExpulsadoDeDeposito) {
                    irAZonaRecuperacion(5000, 10000);
                }

            } catch (InterruptedException e) { 
                // Limpia el flag interno de interrupción de la Máquina Virtual Java. Esto es crucial para
                // devolver el hilo a un estado normal y evitar que las siguientes llamadas a sleep() o wait() fallen automáticamente.
                Thread.interrupted(); 
                continue; 
            }
        }
    }

    // Al declarar 'throws InterruptedException', este método delega la responsabilidad de manejar
    // las interrupciones al método llamador (el bloque try-catch principal del run).
    private void irAZonaRecuperacion(int minMs, int maxMs) throws InterruptedException { 
        activo = false; 
        log.registrarEvento("El delegado " + id + " ha sido expulsado y va a la zona de recuperacion."); 
        sistemaCentral.entrarRecuperacionDelegado(id); 
        Thread.sleep(rand.nextInt(maxMs - minMs) + minMs); 
        sistemaCentral.salirRecuperacionDelegado(id); 
        log.registrarEvento("El delegado " + id + " sale de la zona de recuperacion."); 
        activo = true; 
    }

    private Planeta seleccionarPlaneta(TipoRecurso recurso) {
        // Uso de la API de Streams para realizar un filtrado funcional. Al operar sobre una lista
        // cuyas referencias a objetos no cambian, la iteración es 'Thread-Safe' (segura en concurrencia).
        List<Planeta> candidatos = planetas.stream().filter(p -> p.getRecursoGenerado() == recurso).toList(); 
        return candidatos.get(rand.nextInt(candidatos.size()));
    }

    private Deposito seleccionarDeposito(TipoRecurso recurso) {
        return depositos.stream().filter(d -> d.getTipoRecurso() == recurso).findFirst().orElseThrow();
    }
}