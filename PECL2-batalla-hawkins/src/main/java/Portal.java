// Importación del paquete locks para utilizar mecanismos de sincronización avanzados en lugar del 'synchronized' tradicional
import java.util.concurrent.locks.*;

public class Portal {
    // Nombre de la zona a la que conecta este portal (ej. "BOSQUE")
    private final String destino;
    // Número mínimo de niños que deben juntarse para poder cruzar juntos de Hawkins al Upside Down
    private final int tamanoGrupo;
    
    // Cerrojo (Lock) reentrante con el parámetro 'true' para asegurar "equidad" (fairness). 
    // Esto significa que los hilos que llevan más tiempo esperando serán los primeros en obtener el cerrojo.
    private final ReentrantLock lock = new ReentrantLock(true);
    
    // Variables de "Condición" (Condition) asociadas al cerrojo. Permiten a los hilos esperar (await) hasta que se cumpla una condición específica
    // Condición para esperar a que el grupo se llene
    private final Condition conditionGrupo = lock.newCondition();
    // Condición para que el portal físico (el "tubo") esté libre y un niño pueda pasar
    private final Condition conditionPortalLibre = lock.newCondition();
    // Condición para dar prioridad a los niños que están huyendo del Upside Down
    private final Condition conditionPrioridadVuelta = lock.newCondition();

    // Contadores de estado del portal
    private int ninosEsperando = 0; // Cuántos niños hay actualmente en la cola de ida
    private boolean cruzando = false; // Indica si un grupo ha alcanzado el tamaño necesario y está en proceso de cruzar
    private boolean portalOcupado = false; // Indica si el espacio físico del portal está siendo ocupado por un niño en este milisegundo
    private int ninosPorPasarDelGrupoActual = 0; // Cuántos niños del grupo que está cruzando faltan por salir del portal
    private int esperandoParaVolver = 0; // Cuántos niños están en el Upside Down esperando para volver a Hawkins
    
    // Bandera para el evento global de Apagón
    private boolean apagon = false;

    // Constructor que define el destino y la capacidad del grupo
    public Portal(String destino, int tamanoGrupo) {
        this.destino = destino;
        this.tamanoGrupo = tamanoGrupo;
    }
    
    // Método para activar o desactivar el portal (usado en el evento APAGON)
    public void setApagon(boolean activo) {
        lock.lock(); // Adquiere el cerrojo para modificar el estado de forma segura
        try {
            apagon = activo;
            // Si el apagón ha terminado (activo == false)
            if (!activo) {
                // Despierta a todos los hilos que se quedaron bloqueados en cualquiera de las 3 condiciones debido al apagón
                conditionGrupo.signalAll();
                conditionPortalLibre.signalAll();
                conditionPrioridadVuelta.signalAll();
            }
        } finally {
            // SIEMPRE se libera el cerrojo en un bloque finally para evitar "deadlocks" (bloqueos muertos) si ocurre una excepción
            lock.unlock(); 
        }
    }
    
    // Lógica para que un niño de Hawkins haga cola, forme grupo y comience a cruzar
    public void esperarGrupoYCruzar(Nino n) throws InterruptedException {
    lock.lock();
    try {
        // Si ya hay un grupo cruzando o hay un apagón, el niño debe esperar en la cola del grupo
        while (cruzando || apagon) {
            conditionGrupo.await();
        }

        // El niño se une a la cola de espera
        ninosEsperando++;

        // Si aún no se ha llegado al tamaño mínimo del grupo
        if (ninosEsperando < tamanoGrupo) {
            // El niño espera hasta que la variable 'cruzando' sea verdadera (es decir, hasta que llegue el último niño del grupo)
            while (!cruzando) {
                conditionGrupo.await();
                // Si mientras esperaba hubo un apagón, retrocede su estado de espera hasta que el apagón pase
                if (apagon) {
                    ninosEsperando--;
                    while (apagon) { conditionGrupo.await(); }
                    ninosEsperando++;
                }
            }
        // Si este niño es el que completa el tamaño del grupo
        } else {
            cruzando = true; // Activa la bandera para que el grupo empiece a moverse
            ninosPorPasarDelGrupoActual = tamanoGrupo; // Registra cuántos tienen que pasar
            ninosEsperando = 0; // Reinicia la cola de espera para los siguientes
            conditionGrupo.signalAll(); // Despierta a todos los niños de este grupo que estaban esperando en el 'if' anterior
        }

        // Lógica para cruzar el portal físico uno por uno (en fila india)
        // El niño tiene que esperar si: el portal está ocupado por otra persona, si hay niños intentando huir (tienen prioridad), o si hay apagón
        while (portalOcupado || esperandoParaVolver > 0 || apagon) {
            conditionPortalLibre.await();
        }
        // El niño ocupa el portal
        portalOcupado = true;

    } finally {
        lock.unlock();
    }
}
    
    // Se llama cuando el niño ha terminado de cruzar físicamente (ha pasado su Thread.sleep)
    public void finalizarCruceIda(Nino n) {
        lock.lock();
        try {
            portalOcupado = false; // Libera el espacio físico del portal
            ninosPorPasarDelGrupoActual--; // Descuenta a este niño del grupo
            
            // Si este era el último niño del grupo en cruzar
            if (ninosPorPasarDelGrupoActual == 0) {
                cruzando = false; // El estado de cruce termina
                conditionGrupo.signalAll(); // Avisa a los niños de la cola de espera que ya pueden empezar a formar un nuevo grupo
            }
            
            // Prioridad de vuelta: Si hay niños huyendo del Upside Down, se les avisa primero a ellos
            if (esperandoParaVolver > 0) { conditionPrioridadVuelta.signal(); }
            // Si no hay nadie huyendo, se le avisa al siguiente niño del grupo de ida
            else { conditionPortalLibre.signal(); }
        } finally {
            lock.unlock();
        }
    }

    // Lógica para que un niño huya del Upside Down hacia Hawkins
    public void cruzarHaciaHawkins(Nino n) throws InterruptedException {
        lock.lock();
        try {
            // Se registra en la cola especial de prioridad de vuelta
            esperandoParaVolver++;
            // Espera a que el espacio físico del portal se libere y a que no haya apagón
            while (portalOcupado || apagon) { conditionPrioridadVuelta.await(); }
            // Ocupa el portal
            portalOcupado = true;
            // Ya no está esperando
            esperandoParaVolver--;
        } finally {
            lock.unlock();
        }
    }

    // Se llama cuando el niño que huía ha terminado de cruzar físicamente
    public void finalizarCruceVuelta(Nino n) {
        lock.lock();
        try {
            portalOcupado = false; // Libera el portal
            // Sigue dando prioridad: si quedan más niños huyendo, les avisa a ellos
            if (esperandoParaVolver > 0) { conditionPrioridadVuelta.signal(); }
            // Si ya no queda nadie huyendo, avisa a los de la cola normal (ida)
            else { conditionPortalLibre.signal(); }
        } finally {
            lock.unlock();
        }
    }

    // Devuelve a qué zona conduce este portal
    public String getNombreDestino() { return destino; }
    
    // Métodos Getters para la interfaz gráfica.
    // Todos utilizan lock/unlock rápidamente para leer las variables de forma segura y evitar que otro hilo las modifique a medias
    
    public int getNinosEsperando() {
        lock.lock();
        try { return ninosEsperando; }
        finally { lock.unlock(); }
    }

    public int getNinosCruzando() {
        lock.lock();
        try { return ninosPorPasarDelGrupoActual; }
        finally { lock.unlock(); }
    }

    public int getEsperandoVuelta() {
        lock.lock();
        try { return esperandoParaVolver; }
        finally { lock.unlock(); }
    }
}