package federacion.hilos;

import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
import java.util.List;
import java.util.Random;

// La clase Patrulla hereda de Thread, por lo que cada patrulla actuará como un proceso independiente (concurrente).
// Su misión es viajar continuamente por el universo vigilando zonas y defendiéndolas de los saqueadores.
public class Patrulla extends Thread {

    // Identificador único de la nave patrulla (ej. P001, P002)
    private final String id;
    // Referencia al sistema central para actualizar su estado y que la interfaz gráfica lo pueda mostrar
    private final SistemaCentral sistemaCentral;
    // Lista de todos los planetas mineros del mundo que debe proteger
    private final List<Planeta> planetas;
    // Lista de todos los depósitos orbitales del mundo que debe proteger
    private final List<Deposito> depositos;
    // Gestor para registrar los eventos importantes en el archivo de texto de forma segura
    private final GestorLog log;
    // Herramienta para tomar decisiones aleatorias (a dónde ir y cuánto tiempo patrullar)
    private final Random rand;

    // Constructor para inicializar a la patrulla en el mundo con su ID y sus destinos posibles
    public Patrulla(String id, SistemaCentral sistemaCentral, List<Planeta> planetas,
                    List<Deposito> depositos, GestorLog log) {
        // Le pasamos el ID al constructor de Thread para que el hilo tenga ese nombre en el Sistema Operativo
        super(id);
        this.id = id;
        this.sistemaCentral = sistemaCentral;
        this.planetas = planetas;
        this.depositos = depositos;
        this.log = log;
        this.rand = new Random();
    }

    // El método run() contiene todo el ciclo de vida y la rutina de trabajo de la patrulla
    @Override
    public void run() {
        // Bucle infinito global: la patrulla repetirá su ciclo de Hangar -> Patrullaje -> Hangar hasta cerrar el programa
        while (true) {
            try {
                // ==========================================
                // 1. FASE DE HANGAR (Preparación)
                // ==========================================
                // Avisa al sistema central de que se está preparando
                sistemaCentral.actualizarEstadoPatrulla(id, "Hangar - PREPARANDO");
                // Entra físicamente al Hangar
                sistemaCentral.entrarHangar(id);
                // Simula el tiempo que pasa repostando y preparándose (entre 3 y 6 segundos)
                Thread.sleep(rand.nextInt(3000) + 3000); 
                // Sale del hangar, lista para empezar su vigilancia
                sistemaCentral.salirHangar(id);

                // ==========================================
                // 2. FASE DE PATRULLAJE CONTINUO
                // ==========================================
                // Bucle de patrullaje: viajará de zona en zona ininterrumpidamente hasta que sea derribada
                while (true) {
                    // Elige a dónde ir lanzando un "dado" entre todos los planetas y depósitos
                    Object zonaAsignada = seleccionarZonaAleatoria();
                    // Bandera para saber si en este destino ha perdido un combate contra un saqueador
                    boolean derrotada = false;

                    // --- CASO A: El destino es un PLANETA ---
                    if (zonaAsignada instanceof Planeta p) {
                        sistemaCentral.actualizarEstadoPatrulla(id, p.getNombre() + " - PATRULLANDO");
                        try {
                            // Intenta aterrizar en el planeta. Se pondrá a la cola si está lleno o bajo ataque
                            p.entrarPatrulla(id);
                            // Simula el tiempo de la ronda de vigilancia (entre 2 y 4 segundos)
                            Thread.sleep(rand.nextInt(2000) + 2000);
                            
                            // ¡Punto crítico! La patrulla intenta irse. Si justo ahora hay un saqueador atacando el planeta,
                            // el método salirPatrulla() la dejará bloqueada (wait) defendiendo la zona hasta que acabe el asalto.
                            p.salirPatrulla(id); 
                            
                        } catch (InterruptedException e) {
                            // Si salta esta excepción, significa que el saqueador ha ganado el combate y ha derribado a la patrulla.
                            derrotada = true; 
                            // Como ha sido expulsada violentamente, se limpia de los registros del planeta sin hacer ruido
                            p.bajaPorDerrotaPatrulla(); 
                        }
                        
                    // --- CASO B: El destino es un DEPÓSITO ---
                    } else if (zonaAsignada instanceof Deposito d) {
                        sistemaCentral.actualizarEstadoPatrulla(id, d.getNombre() + " - PATRULLANDO");
                        try {
                            // Intenta acoplarse al depósito (esperando su turno si es necesario)
                            d.entrarPatrulla(id);
                            // Realiza su vigilancia orbital (entre 2 y 4 segundos)
                            Thread.sleep(rand.nextInt(2000) + 2000);
                            // Intenta marcharse (se quedará retenida defendiendo si la zona está bajo ataque)
                            d.salirPatrulla(id);
                        } catch (InterruptedException e) {
                            // Si pierde el combate contra el saqueador en el depósito
                            derrotada = true;
                            // Se borra del depósito de forma segura
                            d.bajaPorDerrotaPatrulla();
                        }
                    }

                    // Después de intentar patrullar la zona (sea planeta o depósito), comprobamos cómo le fue:
                    if (derrotada) {
                        // Si perdió el combate, es evacuada de emergencia a la zona médica
                        irAZonaRecuperacion(8000, 12000);
                        // Rompe el bucle interno de patrullaje. Esto la devuelve al principio del ciclo principal,
                        // obligándola a pasar por el Hangar de nuevo para repararse.
                        break; 
                    }
                }
            } catch (InterruptedException e) {
                // Mecanismo de seguridad: Si la patrulla sufre un "fallo" (interrupción no provocada por un saqueador 
                // durante el combate, por ejemplo, mientras esperaba pacientemente en una cola), 
                // se limpia su estado de error y reinicia su ciclo para no colapsar.
                Thread.interrupted(); 
                continue; 
            }
        }
    }

    // Método que simula la estancia de la patrulla en "el taller" tras sufrir daños críticos
    private void irAZonaRecuperacion(int minMs, int maxMs) throws InterruptedException {
        // Actualiza su letrero para que desde control (UI) sepan que está averiada
        sistemaCentral.actualizarEstadoPatrulla(id, "Recuperación - RECUPERANDO");
        log.registrarEvento("La patrulla " + id + " ha sido derrotada y va a la zona de recuperacion.");
        
        // Ingresa en el monitor de recuperación
        sistemaCentral.entrarRecuperacionPatrulla(id);
        // Simula el tiempo de reparaciones intensivas (entre 8 y 12 segundos)
        Thread.sleep(rand.nextInt(maxMs - minMs) + minMs);
        // Las reparaciones terminan y sale del taller
        sistemaCentral.salirRecuperacionPatrulla(id);
        
        log.registrarEvento("La patrulla " + id + " sale de la zona de recuperacion, lista para ir al Hangar.");
    }

    // Método matemático para mezclar planetas y depósitos y elegir uno a ciegas
    private Object seleccionarZonaAleatoria() {
        // Suma el total de sitios posibles (ej. 5 planetas + 3 depósitos = 8 opciones)
        int index = rand.nextInt(planetas.size() + depositos.size());
        
        // Si el número cae en el rango de los planetas, devuelve un planeta
        if (index < planetas.size()) {
            return planetas.get(index);
        // Si cae por encima, le resta el tamaño de los planetas para devolver el depósito correcto
        } else {
            return depositos.get(index - planetas.size());
        }
    }
}