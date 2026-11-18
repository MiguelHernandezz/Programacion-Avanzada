package federacion.hilos;

import federacion.monitores.BaseSaqueadores;
import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import java.util.Random;

// La clase Saqueador hereda de Thread, representando a una unidad enemiga independiente.
// Su ciclo de vida consiste en salir de su escondite, asediar zonas de la federación, combatir y robar.
public class Saqueador extends Thread {

    // Identificador único del enemigo (ej. S001, S002)
    private final String id;
    // Referencia al monitor de la base pirata, desde donde se coordinan y descansan
    private final BaseSaqueadores base;
    // Gestor para registrar sus fechorías de forma segura en el archivo de texto
    private final GestorLog log;
    // Generador de números aleatorios para decidir resultados de combates, tiempos y botines
    private final Random rand;

    // Constructor que inicializa al saqueador y lo inscribe en el sistema
    public Saqueador(String id, BaseSaqueadores base, GestorLog log) {
        super(id);
        this.id = id;
        this.base = base;
        this.log = log;
        this.rand = new Random();
    }

    // El método run() contiene la rutina ininterrumpida de este enemigo
    @Override
    public void run() {
        // Bucle infinito: el saqueador no se rinde nunca y atacará hasta que termine la simulación
        while (true) {
            try {
                // ==========================================
                // 1. ESPERA EN LA BASE (Preparación)
                // ==========================================
                // Se registra físicamente en la base para que la interfaz sepa que está allí
                base.entrarBase(id);
                // Simula el tiempo que pasa rearmándose y planeando el próximo golpe (3 a 6 segundos)
                Thread.sleep(rand.nextInt(3000) + 3000); 
                // Sale del escondite, listo para causar problemas
                base.salirBase(id);

                // ==========================================
                // 2. SELECCIÓN DE OBJETIVO
                // ==========================================
                // Pide a la base un objetivo válido que no esté siendo atacado ya por otro compañero.
                // Este método le garantiza exclusividad sobre el destino que le devuelva.
                Object objetivo = base.solicitarObjetivo(id);

                // ==========================================
                // 3. ATAQUE
                // ==========================================
                // Viaja al objetivo y ejecuta la lógica de asedio. Devuelve true si ganó, false si huyó herido.
                boolean continuar = ejecutarAtaque(objetivo);

                // ==========================================
                // 4. DESCANSO Y REORGANIZACIÓN
                // ==========================================
                // Si el saqueador ganó el asalto, se toma 10 segundos para celebrar y guardar el botín 
                // antes de iniciar un nuevo ciclo. (Si perdió, el descanso de 20s ya se aplicó dentro de la huida).
                if (continuar) {
                    Thread.sleep(10000);
                }

            } catch (InterruptedException e) {
                // Si la simulación se cierra o se detiene globalmente, el saqueador abandona su rutina pacíficamente.
                break;
            }
        }
    }

    /* * Orquesta el asedio a la zona asignada (Planeta o Depósito).
     * Controla el bloqueo de la zona, el combate inicial y el robo final si resulta victorioso.
     */
    private boolean ejecutarAtaque(Object zona) throws InterruptedException {
        boolean saqueadorVictorioso;

        // --- CASO A: El objetivo es un PLANETA MINERO ---
        if (zona instanceof Planeta p) {
            base.registrarAtaque(id, p.getNombre());
            // ¡Punto crítico! Llama al monitor del planeta para cambiar su estado a "bajoAtaque = true".
            // Esto bloquea las puertas del planeta: nadie nuevo puede entrar y las patrullas/delegados 
            // que intenten salir se quedarán atrapados en un wait() defendiendo o asustados.
            p.bloquearZona(id); 

            // Evalúa las defensas: comprueba de forma segura si hay patrullas retenidas en el planeta
            boolean hayPatrullas = p.getPatrullasPresentes() > 0;
            if (hayPatrullas) {
                // Si hay defensas, inicia la fase de combate pasando el planeta como escenario
                saqueadorVictorioso = combatir(p, null);
            } else {
                // Si el planeta está indefenso, solo tarda 1 segundo en tomar el control de la zona sin resistencia
                Thread.sleep(1000); 
                saqueadorVictorioso = true;
            }

            // Si perdió el combate contra la patrulla...
            if (!saqueadorVictorioso) {
                p.finalizarAtaque(); // Levanta el bloqueo del planeta para que todos puedan volver a la normalidad
                base.finalizarAtaque(id); // Limpia su cartel de "atacando" de la base
                base.liberarObjetivo(zona); // Devuelve el objetivo a la lista para que otro compañero pueda intentarlo
                
                // Huye malherido a la base y cumple una penalización de 20 segundos recuperándose
                base.entrarBase(id);
                Thread.sleep(20000);
                base.salirBase(id);
                return false; // Retorna false para saltarse el descanso de 10s del run()
            }

            // Si ganó el combate (o no había nadie defendiendo), inicia el saqueo
            // Este método lanza un interrupt() a todos los delegados atrapados, asustándolos y forzando su expulsión
            p.ejecutarSaqueo(id);
            log.registrarEvento("El saqueador " + id + " ha interrumpido la extraccion en " + p.getNombre() + ".");
            // Tiempo que tarda en destruir la maquinaria minera antes de irse
            Thread.sleep(1000); 
            // Finaliza su asalto y libera la zona para que vuelva a ser segura
            p.finalizarAtaque();

        // --- CASO B: El objetivo es un DEPÓSITO ORBITAL ---
        } else if (zona instanceof Deposito d) {
            base.registrarAtaque(id, d.getNombre());
            // Bloquea las puertas del depósito (atrapa a todos en un wait)
            d.bloquearZona(id); 

            // Revisa si la federación tiene naves patrullando aquí
            boolean hayPatrullas = d.getPatrullasPresentes() > 0;
            if (hayPatrullas) {
                // Inicia el combate en el escenario del depósito
                saqueadorVictorioso = combatir(null, d);
            } else {
                // Toma de control limpia si no hay defensas
                Thread.sleep(1000);
                saqueadorVictorioso = true;
            }

            // Si es derrotado...
            if (!saqueadorVictorioso) {
                d.finalizarAtaque(); // Libera el depósito
                base.finalizarAtaque(id);
                base.liberarObjetivo(zona);
                
                // Retirada forzosa y hospitalización en la base pirata durante 20 segundos
                base.entrarBase(id);
                Thread.sleep(20000);
                base.salirBase(id);
                return false;
            }

            // Si gana en el depósito, la recompensa es distinta: roba los recursos físicos
            // Expulsa a los delegados que estuvieran descargando
            d.ejecutarSaqueo(id);
            // El monitor del depósito resta la cantidad matemática (entre 10 y 30 unidades) de forma segura
            int cantidadRobada = d.ejecutarRobo(rand.nextInt(21) + 10); 
            log.registrarEvento("Ataque saqueador de " + id + " al " + d.getNombre() + ": " + cantidadRobada + " unidades robadas.");
            d.finalizarAtaque(); // Levanta el asedio
        }

        // Si el código llega hasta aquí, significa que sobrevivió al combate y logró su objetivo
        base.finalizarAtaque(id);
        base.liberarObjetivo(zona);
        return true;
    }

    /* * Subrutina que simula un enfrentamiento directo entre este hilo (Saqueador) y una Patrulla que estaba retenida.
     * El resultado se decide por puro azar matemático.
     */
    private boolean combatir(Planeta planeta, Deposito deposito) throws InterruptedException {
        log.registrarEvento("El saqueador " + id + " entra en combate con una patrulla.");
        // Simula la intensidad de la batalla espacial. Durante estos 6 segundos, tanto el saqueador como la patrulla
        // defensora están paralizados ocupando la zona.
        Thread.sleep(6000);

        // Se lanza una moneda al aire: 50% de probabilidades de ganar o perder
        boolean saqueadorGana = rand.nextBoolean(); 

        if (saqueadorGana) {
            log.registrarEvento("El saqueador " + id + " gana el combate. La patrulla es derrotada.");
            
            // ¡Golpe fatal! El saqueador ordena al monitor de la zona que ejecute un hilo.interrupt() 
            // directamente contra el hilo de la patrulla defensora, rompiendo su wait() y mandándola al hospital.
            if (planeta != null) {
                planeta.expulsarPatrullaEnCombate(); 
            } else if (deposito != null) {
                deposito.expulsarPatrullaEnCombate();
            }

            return true;
        } else {
            // Si la moneda cae del lado de la Patrulla, el saqueador asume la derrota sin tocar el estado de la nave defensora
            log.registrarEvento("El saqueador " + id + " pierde el combate y se va a la base.");
            return false;
        }
    }
}