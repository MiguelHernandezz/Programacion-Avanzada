package federacion.monitores;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Monitor que actúa como punto central de coordinación para todos los saqueadores.
// Gestiona el acceso a la base y la asignación exclusiva de objetivos (zonas de ataque).
public class BaseSaqueadores {

    private final GestorLog log;
    private final List<Planeta> planetas;
    private final List<Deposito> depositos;
    
    // Contadores para que la UI/RMI puedan consultar el estado del sistema en tiempo real
    private int saqueadoresEnBase = 0;
    private int saqueadoresEnCola = 0;
    private int saqueadoresAtacando = 0;

    // Mapa que registra si un planeta o depósito está siendo asaltado en este momento (true = ocupado)
    private final Map<Object, Boolean> zonasOcupadasPorAtaque;
    
    // Mecanismo de turnos (Ticket) para garantizar que los saqueadores esperen en orden estricto de llegada (FIFO)
    private int ticketSaqueador = 0;
    private int turnoSaqueador = 0;
    private final Random rand;
    
    // Listas auxiliares para seguimiento de hilos activos
    private final java.util.List<String> listaSaqueadoresEnBase = new java.util.ArrayList<>();
    private final java.util.Set<String> registroTotalSaqueadores = new java.util.HashSet<>();
    // Mapa concurrente para rastrear qué saqueador está atacando qué zona (usado por la UI para pintar el mapa)
    private final java.util.Map<String, String> ataquesActivos = new java.util.concurrent.ConcurrentHashMap<>();

    // Constructor que inicializa los monitores y marca todas las zonas como "libres" (false) inicialmente
    public BaseSaqueadores(List<Planeta> planetas, List<Deposito> depositos, GestorLog log) {
        this.log = log;
        this.planetas = planetas;
        this.depositos = depositos;
        this.rand = new Random();
        this.zonasOcupadasPorAtaque = new HashMap<>();
        
        for (Planeta p : planetas) zonasOcupadasPorAtaque.put(p, false);
        for (Deposito d : depositos) zonasOcupadasPorAtaque.put(d, false);
    }

    public synchronized int getSaqueadoresEnBase() { return saqueadoresEnBase; }
    public synchronized int getSaqueadoresEnCola() { return saqueadoresEnCola; }
    public synchronized int getSaqueadoresAtacando() { return saqueadoresAtacando; }
    public synchronized int getTotalSaqueadores() { return registroTotalSaqueadores.size(); }
    
    public synchronized String getTextSaqueadoresBase() { 
        return String.join("  ", listaSaqueadoresEnBase); 
    }

    // Registra al saqueador cuando vuelve a la base (método sincronizado para evitar condiciones de carrera)
    public synchronized void entrarBase(String id) {
        saqueadoresEnBase++;
        listaSaqueadoresEnBase.add(id); 
        registroTotalSaqueadores.add(id); 
        log.registrarEvento("El saqueador " + id + " espera en la base.");
    }

    // Registra la salida del saqueador hacia una zona de ataque
    public synchronized void salirBase(String id) {
        saqueadoresEnBase--;
        listaSaqueadoresEnBase.remove(id); 
    }

    // Método principal: el saqueador pide un objetivo. Si todas las zonas están ocupadas, el hilo entra en wait()
    public synchronized Object solicitarObjetivo(String id) throws InterruptedException {
        int miTicket = ticketSaqueador++; // Toma su número de turno
        saqueadoresEnCola++; 

        try {
            // El hilo se suspende si no es su turno o si no hay ninguna zona libre para atacar
            while (miTicket != turnoSaqueador || estanTodasLasZonasOcupadas()) {
                wait();
            }

            Object objetivo = seleccionarZonaLibre(); // Elige una zona al azar entre las disponibles
            
            zonasOcupadasPorAtaque.put(objetivo, true); // Marca la zona como ocupada para evitar ataques dobles
            
            turnoSaqueador++; // Pasa el turno al siguiente saqueador en la cola
            saqueadoresAtacando++;
            notifyAll(); // Despierta a los demás saqueadores para que vuelvan a evaluar si es su turno
            return objetivo;

        } catch (InterruptedException e) {
            turnoSaqueador++; // Si el saqueador se interrumpe, libera su turno para no bloquear a nadie
            notifyAll();
            throw e;
        } finally {
            saqueadoresEnCola--;
        }
    }

    // Libera una zona que estaba siendo atacada, permitiendo que nuevos saqueadores vuelvan a pedirla
    public synchronized void liberarObjetivo(Object objetivo) {
        saqueadoresAtacando--;
        zonasOcupadasPorAtaque.put(objetivo, false); // Marca la zona como disponible
        notifyAll(); // Despierta a los saqueadores en wait() para que vean que hay una zona libre
    }

    // Comprueba si hay zonas disponibles para atacar (false si hay al menos una libre)
    private boolean estanTodasLasZonasOcupadas() {
        for (Boolean ocupada : zonasOcupadasPorAtaque.values()) {
            if (!ocupada) return false; 
        }
        return true;
    }

    // Selecciona una zona libre aplicando una preferencia probabilística
    private Object seleccionarZonaLibre() {
        boolean prefiereDeposito = rand.nextInt(100) < 70; // 70% de probabilidad de buscar depósitos antes que planetas

        Object objetivo = null;
        if (prefiereDeposito) {
            objetivo = buscarDepositoLibre();
            if (objetivo == null) objetivo = buscarPlanetaLibre(); // Si no hay depósitos, busca planeta
        } else {
            objetivo = buscarPlanetaLibre();
            if (objetivo == null) objetivo = buscarDepositoLibre(); // Si no hay planetas, busca depósito
        }
        return objetivo;
    }

    // Filtra la lista de depósitos y elige uno al azar entre los que no están ocupados
    private Deposito buscarDepositoLibre() {
        List<Deposito> libres = new ArrayList<>();
        for (Deposito d : depositos) {
            if (!zonasOcupadasPorAtaque.get(d)) libres.add(d);
        }
        if (libres.isEmpty()) return null;
        return libres.get(rand.nextInt(libres.size())); 
    }

    // Filtra la lista de planetas y elige uno al azar entre los que no están ocupados
    private Planeta buscarPlanetaLibre() {
        List<Planeta> libres = new ArrayList<>();
        for (Planeta p : planetas) {
            if (!zonasOcupadasPorAtaque.get(p)) libres.add(p);
        }
        if (libres.isEmpty()) return null;
        return libres.get(rand.nextInt(libres.size()));
    }
    
    // Registra en el mapa concurrente que este saqueador está ocupando una zona específica
    public void registrarAtaque(String idSaqueador, String nombreZona) {
        ataquesActivos.put(idSaqueador, nombreZona);
    }

    // Borra al saqueador del registro de ataques al terminar
    public void finalizarAtaque(String idSaqueador) {
        ataquesActivos.remove(idSaqueador);
    }

    // Genera un resumen textual de todos los ataques activos para mostrar en la interfaz
    public String getTextAtaquesActivos() {
        if (ataquesActivos.isEmpty()) {
            return "Ningún ataque activo en este momento.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : ataquesActivos.entrySet()) {
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}