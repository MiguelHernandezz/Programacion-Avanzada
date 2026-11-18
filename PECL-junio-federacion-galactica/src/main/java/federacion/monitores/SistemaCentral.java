package federacion.monitores;

// La clase SistemaCentral actúa como un Monitor de registro global (la "torre de control").
// Su misión es llevar la cuenta exacta de en qué lugar seguro (Hangar, Centro o Taller) está cada nave,
// asegurando que la lectura de estos datos para la pantalla no choque con la escritura de los hilos.
public class SistemaCentral {

    // Herramienta para dejar constancia escrita de los movimientos en el archivo físico
    private final GestorLog log;

    // Listas de control que actúan como el "radar" de la Federación.
    // Guardan el ID (ej. D001, P005) de los hilos que están físicamente en estas zonas de descanso o preparación.
    private final java.util.List<String> listaDelegadosCentro = new java.util.ArrayList<>();
    private final java.util.List<String> listaPatrullasHangar = new java.util.ArrayList<>();
    // La zona de recuperación es un hospital/taller compartido, por lo que mezcla IDs de patrullas y delegados.
    private final java.util.List<String> listaRecuperacion = new java.util.ArrayList<>();
    
    // Mapa concurrente. A diferencia de las listas normales, el ConcurrentHashMap de Java está 
    // diseñado para soportar que múltiples hilos lean y escriban a la vez de forma ultra rápida sin colgarse,
    // por lo que no necesitará bloques 'synchronized' explícitos más abajo.
    private final java.util.Map<String, String> estadoPatrullas = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Constructor que inyecta el gestor del archivo de texto
    public SistemaCentral(GestorLog log) {
        this.log = log;
    }

    // ==========================================
    // GETTERS PARA LA INTERFAZ GRÁFICA / RMI
    // ==========================================
    // Métodos de consulta sincronizados. Al exigir el cerrojo del monitor, aseguramos que la interfaz 
    // gráfica no lea una lista justo en el milisegundo en que un hilo está añadiéndose o borrándose.
    // Esto evita el temido error "ConcurrentModificationException" que hace crashear las aplicaciones.
    public synchronized int getDelegadosEnCentro() { return listaDelegadosCentro.size(); }
    public synchronized int getPatrullasEnHangar() { return listaPatrullasHangar.size(); }
    
    // Truco usando la API de Streams: como la zona médica es compartida, cuenta solo cuántos IDs empiezan por 'D' (Delegados)
    public synchronized int getDelegadosEnRecuperacion() { 
        return (int) listaRecuperacion.stream().filter(id -> id.startsWith("D")).count(); 
    }
    // Hace lo mismo pero filtrando los IDs que empiezan por 'P' (Patrullas)
    public synchronized int getPatrullasEnRecuperacion() { 
        return (int) listaRecuperacion.stream().filter(id -> id.startsWith("P")).count(); 
    }
    
    // Une todos los nombres de la lista separados por espacios para que el panel de texto de la UI los pinte de golpe
    public synchronized String getTextDelegadosCentro() { return String.join("  ", listaDelegadosCentro); }
    public synchronized String getTextPatrullasHangar() { return String.join("  ", listaPatrullasHangar); }
    public synchronized String getTextRecuperacion() { return String.join("  ", listaRecuperacion); }

    // ==========================================
    // ZONA 1: CENTRO DE COORDINACIÓN (Delegados)
    // ==========================================
    // Fija la posición del delegado en el mapa al iniciar su ciclo de trabajo
    public synchronized void entrarCentroCoordinacion(String idDelegado) {
        listaDelegadosCentro.add(idDelegado);
        log.registrarEvento("El delegado " + idDelegado + " está en el Centro de Coordinación preparando solicitudes.");
    }

    // Borra su rastro de la base cuando sale a viajar hacia los planetas
    public synchronized void salirCentroCoordinacion(String idDelegado) {
        listaDelegadosCentro.remove(idDelegado);
    }

    // ==========================================
    // ZONA 2: HANGAR (Patrullas)
    // ==========================================
    // Anota a la nave patrulla en el registro del hangar mientras se prepara
    public synchronized void entrarHangar(String idPatrulla) {
        listaPatrullasHangar.add(idPatrulla);
        log.registrarEvento("La patrulla " + idPatrulla + " entra al Hangar de Patrullas.");
    }

    // Elimina a la patrulla del hangar cuando despega a realizar su ronda
    public synchronized void salirHangar(String idPatrulla) {
        listaPatrullasHangar.remove(idPatrulla);
    }

    // ==========================================
    // ZONA 3: ZONA DE RECUPERACIÓN (Hospital / Taller)
    // ==========================================
    // Ingresa a un delegado malherido tras ser asaltado en un planeta o depósito
    public synchronized void entrarRecuperacionDelegado(String idDelegado) {
        listaRecuperacion.add(idDelegado);
        log.registrarEvento("El delegado " + idDelegado + " entra en la Zona de Recuperación.");
    }

    // Da el alta médica al delegado para que vuelva al Centro de Coordinación
    public synchronized void salirRecuperacionDelegado(String idDelegado) {
        listaRecuperacion.remove(idDelegado);
    }

    // Ingresa a una nave patrulla que ha sido destrozada en combate por un pirata
    public synchronized void entrarRecuperacionPatrulla(String idPatrulla) {
        listaRecuperacion.add(idPatrulla);
        log.registrarEvento("La patrulla " + idPatrulla + " entra en la Zona de Recuperación tras ser derrotada.");
    }

    // Los mecánicos terminan las reparaciones y la patrulla vuelve al Hangar
    public synchronized void salirRecuperacionPatrulla(String idPatrulla) {
        listaRecuperacion.remove(idPatrulla);
    }
    
    // ==========================================
    // RASTREO DINÁMICO DE PATRULLAS (GPS)
    // ==========================================
    // Este método es llamado constantemente por las patrullas cada vez que llegan a un planeta o depósito nuevo.
    // Como usa un ConcurrentHashMap, no necesita 'synchronized'. Esto es genial porque evita que
    // cientos de actualizaciones de estado hagan cuello de botella en este monitor central.
    public void actualizarEstadoPatrulla(String id, String estado) {
        estadoPatrullas.put(id, estado);
    }

    // Recorre el mapa concurrente y monta un bloque de texto con el formato "P001 -> Planeta Cryon - PATRULLANDO"
    // para que la interfaz gráfica pueda mostrar exactamente qué está haciendo cada patrulla en tiempo real.
    public String getTextEstadoPatrullas() {
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, String> entry : estadoPatrullas.entrySet()) {
            sb.append(entry.getKey()).append(" -> ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
}