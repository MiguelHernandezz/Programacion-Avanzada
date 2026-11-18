package federacion.hilos;

// Importaciones de los monitores que actúan como recursos compartidos protegidos
import federacion.monitores.BaseSaqueadores;
import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
// Importaciones de utilidades estándar de Java
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Clase encargada de orquestar la creación y el despliegue de los distintos actores (hilos) de la simulación
public class GeneradorActores {

    // Referencias inmutables a los monitores del sistema a los que los actores deberán acceder
    private final SistemaCentral sistemaCentral;
    private final BaseSaqueadores baseSaqueadores;
    private final List<Planeta> planetas;
    private final List<Deposito> depositos;
    private final GestorLog log;

    // Contadores históricos absolutos para asignar identificadores únicos (ej. D001, P002)
    private int contDelegados = 0;
    private int contPatrullas = 0;
    private int contSaqueadores = 0;
    
    // Listas locales al generador para rastrear los hilos que han sido creados.
    // Como cada lista solo es modificada por su propio hilo creador, no necesitan ser colecciones concurrentes.
    private final List<Delegado> listaDelegados = new ArrayList<>();
    private final List<Saqueador> listaSaqueadores = new ArrayList<>();

    // Constructor que inyecta las dependencias (los monitores ya instanciados desde el Main)
    public GeneradorActores(SistemaCentral sistemaCentral, BaseSaqueadores baseSaqueadores, 
                            List<Planeta> planetas, List<Deposito> depositos, GestorLog log) {
        this.sistemaCentral = sistemaCentral;
        this.baseSaqueadores = baseSaqueadores;
        this.planetas = planetas;
        this.depositos = depositos;
        this.log = log;
    }

    // Método principal que arranca la simulación disparando tres hilos independientes (uno por cada tipo de actor)
    public void iniciarGeneracion() {
        
        // 1. Generador de delegados
        // Se crea un hilo anónimo que correrá en segundo plano continuamente evaluando si hacen falta más delegados
        new Thread(() -> { 
            while (true) {
                try {
                    boolean puedeGenerar = false;
                    // Uso de la API de Streams para contar cuántos delegados tienen su bandera interna 'activo' en true.
                    // Esto permite ignorar temporalmente a los que están cumpliendo un castigo en la Zona de Recuperación.
                    long delegadosActivos = listaDelegados.stream().filter(Delegado::isActivo).count();

                    // Los primeros 10 delegados se generan siempre al inicio de la simulación para poblar el sistema
                    if (contDelegados < 10) {
                        puedeGenerar = true; 
                    // Una vez superados los 10 iniciales, se impone un límite máximo estricto de 20 delegados activos simultáneos
                    } else if (delegadosActivos < 20) {
                        // Se itera sobre los monitores de los depósitos para comprobar su nivel de reservas
                        for (Deposito d : depositos) {
                            // Se invoca un método 'synchronized' del monitor. Si algún depósito está por debajo del 10%, se activa la alarma
                            if (d.requiereRefuerzosComerciales()) { 
                                puedeGenerar = true;
                                break; // Basta con que un depósito esté en alerta para autorizar la creación
                            }
                        }
                    }

                    // Si se cumplen las condiciones lógicas, se procede a instanciar un nuevo hilo
                    if (puedeGenerar) {
                        contDelegados++;
                        // Formatea el ID con ceros a la izquierda (ej. "D015")
                        String id = String.format("D%03d", contDelegados);
                        Delegado d = new Delegado(id, sistemaCentral, planetas, depositos, log);
                        listaDelegados.add(d); // Se registra en la lista de rastreo
                        // Se inscribe el hilo en el planificador del Sistema Operativo y comienza a ejecutar su método run() en paralelo
                        d.start(); 
                    }
                    
                    // El hilo generador se duerme durante 2 segundos. 
                    // Esto espacia la creación de delegados para que no aparezcan todos de golpe y no sature la CPU (Busy Waiting).
                    Thread.sleep(2000); 
                } catch (InterruptedException e) { 
                    // Si el hilo orquestador es interrumpido, sale del bucle infinito para permitir que termine limpiamente
                    break; 
                } 
            }
        }).start(); // Se arranca el hilo anónimo inmediatamente después de definirlo

        // 2. Generador inicial de patrullas
        // Las patrullas posteriores se encarga de crearlas el "OrquestadorRefuerzos", aquí solo se lanzan las 2 primeras
        new Thread(() -> { 
            try {
                for (int i = 0; i < 2; i++) {
                    contPatrullas++;
                    String id = String.format("P%03d", contPatrullas);
                    Patrulla p = new Patrulla(id, sistemaCentral, planetas, depositos, log);
                    p.start(); // Despliega la patrulla de forma asíncrona
                    // Pausa de 5 segundos entre la primera y la segunda patrulla para escalonar su llegada a los monitores
                    Thread.sleep(5000); 
                }
            } catch (InterruptedException e) { }
        }).start();

        // 3. Generador de saqueadores
        // Hilo independiente dedicado exclusivamente a gestionar la "horda" de enemigos
        new Thread(() -> { 
            Random rand = new Random();
            while (true) {
                try {
                    // Mantenimiento de la lista: Elimina de la colección a los saqueadores cuyo hilo ya ha terminado de ejecutarse (están muertos).
                    // El método isAlive() es nativo de Java Thread y devuelve false si el run() ha finalizado.
                    listaSaqueadores.removeIf(s -> !s.isAlive()); 
                    
                    // Control de densidad de enemigos: Solo se permiten 40 saqueadores operando simultáneamente en el universo
                    if (listaSaqueadores.size() < 40) { 
                        contSaqueadores++;
                        String id = String.format("S%03d", contSaqueadores);
                        Saqueador s = new Saqueador(id, baseSaqueadores, log);
                        listaSaqueadores.add(s);
                        s.start(); // El saqueador cobra vida y empieza a atacar
                    }
                    
                    // Tiempo aleatorio de generación: Los ataques ocurren en intervalos irregulares de entre 10 y 20 segundos
                    Thread.sleep(rand.nextInt(10000) + 10000); 
                } catch (InterruptedException e) { 
                    // Cierre limpio en caso de interrupción del sistema
                    break; 
                }
            }
        }).start();
    }
}