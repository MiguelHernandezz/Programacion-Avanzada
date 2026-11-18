package federacion.monitores;

import federacion.modelo.TipoRecurso;
import java.util.ArrayList;
import java.util.List;

// La clase Deposito actúa como un "Monitor" clásico en concurrencia.
// Es una zona crítica de memoria compartida. Al usar métodos 'synchronized', el depósito funciona
// como una habitación con un solo cerrojo: solo un hilo (nave) puede entrar a alterar los números a la vez.
public class Deposito {

    // Características inmutables del depósito
    private final String nombre;
    private final TipoRecurso tipoRecurso;
    private final int capacidadMaxima;
    
    // Variables críticas de estado que los hilos van a leer y modificar constantemente
    private int cantidadAlmacenada;
    // 'espacioReservado' es un truco matemático vital. Cuando un delegado está en proceso de descargar,
    // "reserva" el volumen de su carga para que los que esperan en la cola no piensen que hay hueco y colapsen la capacidad.
    private int espacioReservado = 0;
    private final GestorLog log;

    // Control de delegados (Límites de aforo)
    private final int MAX_DELEGADOS = 3;
    private int delegadosDepositando = 0;
    
    // Las 'colas' físicas sirven para imponer un orden estricto de llegada (FIFO).
    // Si no usamos estas listas, el planificador de Java despertaría a los hilos al azar, creando injusticias.
    private final List<Thread> colaDelegados = new ArrayList<>();
    // Los 'registros' sirven para saber exactamente qué hilos están dentro del monitor en este instante.
    // Esto permite a los saqueadores apuntar sus ataques (interrupt) específicamente a los que están dentro.
    private final List<Thread> hilosDelegados;

    // Control de Patrullas defensivas
    private final int MAX_PATRULLAS = 3;
    private int patrullasPresentes = 0;
    
    // Fila física y registro de patrullas para el combate
    private final List<Thread> colaPatrullas = new ArrayList<>();
    private final List<Thread> hilosPatrullas = new ArrayList<>();

    // Bandera de emergencia global. Si está a true, se cierran las puertas para nuevas naves
    // y las patrullas que están dentro quedan atrapadas defendiendo.
    private boolean bajoAtaque = false;

    // Constructor que inicializa el depósito con sus capacidades y reservas iniciales
    public Deposito(String nombre, TipoRecurso tipo, int capacidadMaxima, int cantidadInicial, GestorLog log) {
        this.nombre = nombre;
        this.tipoRecurso = tipo;
        this.capacidadMaxima = capacidadMaxima;
        this.cantidadAlmacenada = cantidadInicial;
        this.log = log;
        this.hilosDelegados = new ArrayList<>();
    }

    // Getters sincronizados. Es obligatorio que sean 'synchronized' para evitar
    // que la interfaz gráfica (o el Orquestador) lea valores a medio actualizar.
    public String getNombre() { return nombre; }
    public TipoRecurso getTipoRecurso() { return tipoRecurso; }
    public synchronized int getCantidadAlmacenada() { return cantidadAlmacenada; }
    public synchronized int getCapacidadMaxima() { return capacidadMaxima; }
    public synchronized int getDelegadosPresentes() { return delegadosDepositando; }
    public synchronized int getPatrullasPresentes() { return patrullasPresentes; }
    public synchronized boolean isBajoAtaque() { return bajoAtaque; }
    
    public synchronized int getDelegadosEnCola() { return colaDelegados.size(); }

    // Método que usa el OrquestadorRefuerzos para saber si tiene que mandar fabricar delegados
    public synchronized boolean requiereRefuerzosComerciales() {
        return cantidadAlmacenada < (capacidadMaxima * 0.10);
    }

    // ==========================================
    // ACCESO DE DELEGADOS (Zona Comercial)
    // ==========================================
    public synchronized void entrarADepositar(String idDelegado, int cantidadAportada) throws InterruptedException {
        // Nada más llegar, el delegado se pone al final de la cola (índice mayor)
        colaDelegados.add(Thread.currentThread()); 

        try {
            // EVALUACIÓN DE BLOQUEO. El hilo se quedará dormido soltando el cerrojo (wait) si ocurre ALGUNA de estas cosas:
            // 1. El almacén está lleno de naves (>= 3).
            // 2. Hay un saqueador atacando el depósito.
            // 3. No es el primero de la cola (evita que los recién llegados se cuelen).
            // 4. Lo que hay dentro + lo que otros están descargando + su propia carga supera el límite físico del almacén.
            while (delegadosDepositando >= MAX_DELEGADOS || bajoAtaque || colaDelegados.get(0) != Thread.currentThread() ||
                   (cantidadAlmacenada + espacioReservado + cantidadAportada > capacidadMaxima)) {
                wait();
            }

            // Si pasa el wait, es su turno y tiene hueco. Entra con éxito.
            colaDelegados.remove(Thread.currentThread());
            delegadosDepositando++;
            hilosDelegados.add(Thread.currentThread());
            
            // Reservamos el espacio aritméticamente. De esta forma, aunque el hilo se vaya a dormir en su 'run()',
            // el monitor ya cuenta con esta carga y no dejará entrar a otro que intente sobrepasar la capacidad.
            espacioReservado += cantidadAportada; 
            
            log.registrarEvento("El delegado " + idDelegado + " comienza a depositar en " + nombre + ".");
            // Al moverse de la cola, avisa al resto para que el siguiente (ahora en la posición 0) evalúe si puede entrar
            notifyAll(); 
            
        } catch (InterruptedException e) {
            // Si el delegado estaba haciendo cola en el 'wait' y un saqueador asalta la zona,
            // el pánico hace que abandone la cola y huya. Debe avisar (notifyAll) para no dejar la cola atascada.
            colaDelegados.remove(Thread.currentThread());
            notifyAll(); 
            throw e; 
        }
    }

    // Se llama cuando el delegado ha terminado de descargar físicamente y va a salir
    public synchronized void salirDeDepositar(String idDelegado, int cantidadAportada, boolean fueExpulsado) {
        // En cualquier caso, libera su reserva aritmética y su plaza en el almacén
        espacioReservado -= cantidadAportada;
        delegadosDepositando--; 
        hilosDelegados.remove(Thread.currentThread());

        // Si terminó de forma natural (no fue asaltado a mitad del proceso)
        if (!fueExpulsado) {
            // Ahora sí, convierte la "reserva" en mercancía real dentro de las bóvedas
            cantidadAlmacenada += cantidadAportada; 
            log.registrarEvento("El delegado " + idDelegado + " ha depositado " + cantidadAportada +
                                " unidades en " + nombre + ". Total: " + cantidadAlmacenada);
        }
        // Despierta a la cola para que entre el siguiente trabajador o patrulla
        notifyAll(); 
    }

    // ==========================================
    // ACCESO DE PATRULLAS (Zona Defensiva)
    // ==========================================
    public synchronized void entrarPatrulla(String idPatrulla) throws InterruptedException {
        // Se pone a la cola exclusiva de patrullas
        colaPatrullas.add(Thread.currentThread()); 
        
        try {
            // Espera si el cupo de defensa está lleno, si hay un ataque, o si hay otras patrullas delante
            while (patrullasPresentes >= MAX_PATRULLAS || bajoAtaque || colaPatrullas.get(0) != Thread.currentThread()) {
                wait();
            }
            // Entra a la órbita del depósito
            colaPatrullas.remove(Thread.currentThread());
            patrullasPresentes++;
            hilosPatrullas.add(Thread.currentThread()); 
            log.registrarEvento("La patrulla " + idPatrulla + " llega a " + nombre + ".");
            notifyAll();
            
        } catch (InterruptedException e) {
            // Si huye mientras hacía cola, se limpia para no atascar al resto
            colaPatrullas.remove(Thread.currentThread()); 
            notifyAll();
            throw e;
        }
    }

    public synchronized void salirPatrulla(String idPatrulla) throws InterruptedException {
        // MECANISMO HEROICO: Si una patrulla intenta salir (su turno de guardia terminó),
        // pero detecta que el depósito está sufriendo un asalto, se queda físicamente bloqueada
        // en este 'wait()', reteniendo al hilo para defender la instalación a toda costa.
        while (bajoAtaque) {
            wait(); 
        }
        
        // ESCUDO DE SEGURIDAD. Solo restamos los contadores si confirmamos que la patrulla
        // estaba realmente registrada en nuestra lista interna. Esto previene el fallo visual de "-1/3"
        // si un hilo se vuelve loco y llama a salir sin haber entrado correctamente.
        if (hilosPatrullas.contains(Thread.currentThread())) {
            patrullasPresentes--;
            hilosPatrullas.remove(Thread.currentThread());
            log.registrarEvento("La patrulla " + idPatrulla + " abandona " + nombre + ".");
            notifyAll();
        }
    }
    
    // Método de limpieza de emergencia que llama la patrulla cuando pierde un combate contra un pirata
    public synchronized void bajaPorDerrotaPatrulla() {
        // Mismo escudo de seguridad para evitar números negativos
        if (hilosPatrullas.contains(Thread.currentThread())) {
            patrullasPresentes--;
            hilosPatrullas.remove(Thread.currentThread());
            notifyAll();
        }
    }

    // ==========================================
    // ATAQUES DE SAQUEADORES (Eventos Hostiles)
    // ==========================================
    
    // 1. El saqueador llega y cierra herméticamente el depósito
    public synchronized void bloquearZona(String idSaqueador) {
        bajoAtaque = true; // Activa la bandera de asedio. Nadie entra, nadie sale.
        log.registrarEvento("¡ALERTA! El saqueador " + idSaqueador + " llega a " + nombre + " y bloquea la zona.");
        notifyAll(); // Despierta a todos para que lean la bandera (los que hacían cola huirán)
    }

    // 2. Ejecución del robo a los delegados (Solo se llama si el pirata ganó a las patrullas)
    public synchronized void ejecutarSaqueo(String idSaqueador) {
        log.registrarEvento("El saqueador " + idSaqueador + " inicia la fase de saqueo en " + nombre + ".");
        // Itera sobre los delegados que estaban depositando en este instante
        for (Thread hilo : hilosDelegados) {
            // Dispara una interrupción letal al hilo del delegado. 
            // Esto romperá su Thread.sleep() y le obligará a ejecutar su bloque catch y huir herido.
            hilo.interrupt(); 
        }
        notifyAll();
    }

    // 3. Ejecución de daño a la patrulla defensora (Llamado durante el combate a cara de perro)
    public synchronized void expulsarPatrullaEnCombate() {
        if (!hilosPatrullas.isEmpty()) {
            // Selecciona a la primera patrulla defensora de la lista y le dispara
            Thread patrullaDerrotada = hilosPatrullas.get(0);
            patrullaDerrotada.interrupt(); // Rompe su heroico 'wait()' forzando su derrota
        }
    }

    // 4. Robo físico de materiales del depósito
    public synchronized int ejecutarRobo(int cantidadDeseada) {
        // Se lleva lo que quiere, pero nunca más de lo que realmente hay (Math.min)
        int cantidadRobada = Math.min(cantidadAlmacenada, cantidadDeseada);
        cantidadAlmacenada -= cantidadRobada; // El almacén sufre la pérdida
        return cantidadRobada;
    }

    // 5. El saqueador se retira (vencedor o vencido) y el depósito vuelve a operar con normalidad
    public synchronized void finalizarAtaque() {
        bajoAtaque = false; // Baja la bandera roja
        log.registrarEvento("Ataque finalizado en " + nombre + ". Zona liberada.");
        notifyAll(); // Despierta a las naves supervivientes y a la cola
    }

    // ==========================================
    // CONSUMO DE RECURSOS (Fábrica de Patrullas)
    // ==========================================
    
    // Método usado por el Orquestador para llevarse el material atómicamente y fabricar naves
    public synchronized boolean consumirRecursos(int cantidadRequerida) {
        if (cantidadAlmacenada >= cantidadRequerida) {
            cantidadAlmacenada -= cantidadRequerida;
            log.registrarEvento("Se han consumido " + cantidadRequerida + " unidades de " + nombre + " para refuerzos.");
            // Al llevarse material, deja espacio libre nuevo, por lo que es vital despertar
            // a la cola de delegados por si alguno estaba bloqueado por falta de espacio
            notifyAll(); 
            return true;
        }
        return false;
    }

    // Mecanismo de 'Rollback' (Compensación) del Orquestador si el ensamblaje de la nave falla a medias
    public synchronized void devolverRecursos(int cantidad) {
        // Devuelve los recursos robados, pero protegiendo matemáticamente la capacidad máxima por si acaso
        cantidadAlmacenada = Math.min(cantidadAlmacenada + cantidad, capacidadMaxima);
        notifyAll();
    }

    // Comando remoto usado desde la red (RMI) por un administrador externo
    public synchronized void vaciarDeposito() {
        cantidadAlmacenada = 0; // Borra de un plumazo todo el inventario
        log.registrarEvento("El depósito " + nombre + " ha sido vaciado remotamente.");
        // Al vaciarlo, se genera muchísimo espacio libre, hay que avisar a los delegados en cola urgentemente
        notifyAll();
    }
}