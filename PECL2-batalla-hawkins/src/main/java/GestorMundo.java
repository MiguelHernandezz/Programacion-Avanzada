// Importaciones de clases necesarias para manejo de archivos, fechas, listas, concurrencia (hilos) y RMI (red)
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RemoteException;

// GestorMundo es el núcleo central de la simulación. 
// Hereda de UnicastRemoteObject e implementa InterfazRemota para permitir que clientes remotos (como la interfaz gráfica) se conecten a él.
public class GestorMundo extends UnicastRemoteObject implements InterfazRemota{

    // contadores de niños en hawkins
    // Se usa CopyOnWriteArrayList porque es "Thread-Safe" (seguro para hilos), evitando errores cuando muchos hilos leen o modifican la lista a la vez
    private List<Nino> ninosCallePrincipal = new CopyOnWriteArrayList<>();;
    private List<Nino> ninosSotanoByers = new CopyOnWriteArrayList<>();;
    private List<Nino> ninosRadioWsqk = new CopyOnWriteArrayList<>();;

    // portales
    // Instancias de los diferentes portales que conectan Hawkins con el Upside Down, definiendo su nombre y capacidad máxima
    private final Portal portalBosque = new Portal("BOSQUE", 2);
    private final Portal portalLaboratorio = new Portal("LABORATORIO", 3);
    private final Portal portalCentroComercial = new Portal("CENTRO COMERCIAL", 4);
    private final Portal portalAlcantarillado = new Portal("ALCANTARILLADO", 2);

    // Contadores del upside down de niños y demogorgons
    // Listas seguras para concurrencia que guardan qué niños están en qué zona del Upside Down
    private final List<Nino> ninosEnBosque = new  CopyOnWriteArrayList<>();
    private final List<Nino> ninosEnLaboratorio = new  CopyOnWriteArrayList<>();
    private final List<Nino> ninosEnCentroComercial = new CopyOnWriteArrayList<>();
    private final List<Nino> ninosEnAlcantarillado = new CopyOnWriteArrayList<>();
    // Array auxiliar con los nombres exactos de las zonas
    private final String[] nombresZonas = {"BOSQUE", "LABORATORIO", "CENTRO COMERCIAL", "ALCANTARILLADO"};
    
    // Listas seguras para concurrencia que guardan qué demogorgons están en qué zona
    private final List<Demogorgon> demosBosque = new CopyOnWriteArrayList<>();
    private final List<Demogorgon> demosLaboratorio = new CopyOnWriteArrayList<>();
    private final List<Demogorgon> demosCentroComercial = new CopyOnWriteArrayList<>();
    private final List<Demogorgon> demosAlcantarillado = new CopyOnWriteArrayList<>();
    
    
    // Contadores "Atómicos" que permiten incrementar valores de forma segura cuando hay múltiples hilos
    private final AtomicInteger contadorIdsDemogorgon = new AtomicInteger(1); 
    private final AtomicInteger capturasTotalesColmena = new AtomicInteger(0);
    
    
    // Lista que representa la colmena donde son llevados los niños capturados
    private final List<Nino> colmena = new CopyOnWriteArrayList<>();
    // Contador global de la sangre donada en la Radio, que luego servirá para que Eleven rescate niños
    private int sangreTotal = 0;

    // log
    // Configuración para el archivo de registro (log) y el formato de fecha/hora
    private final String RUTA_LOG = "hawkins.txt";
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private boolean primerEventoLog = true;
    
    // Eventos globales
    // Banderas booleanas para saber qué evento especial está ocurriendo en este momento
    private boolean apagonActivo = false;
    private boolean tormentaActiva = false;
    private boolean redMentalActiva = false;
    private boolean elevenActiva = false;
    // Objeto utilizado como "monitor" (candado) específicamente para pausar hilos cuando interviene Eleven
    private final Object monitorEleven = new Object(); 
    
    // Bandera principal para pausar todo el mundo
    private boolean pausado = false;
    
    // ranking
    // Lista estándar para almacenar a todos los demogorgons y poder ordenarlos por capturas
    private final java.util.List<Demogorgon> listaDemogorgons = new java.util.ArrayList<>();

    // eventos
    // Variables para enviar la información del evento activo a la interfaz gráfica remota
    private String nombreEventoActual = "Ninguno";
    private long tiempoFinEventoMs = 0; // Guardará el milisegundo exacto en el que acaba

    // Constructor de la clase, lanza RemoteException por ser un objeto RMI
    public GestorMundo()throws RemoteException {
        super();
        // Al iniciar, intenta sobrescribir (append = false) el archivo de log con el título inicial
        try (PrintWriter writer = new PrintWriter(new FileWriter(RUTA_LOG, false))) {
            writer.println("INICIO DE LA BATALLA DE HAWKINS");
        } catch (IOException e) {
            System.err.println("Error al inicializar el log: " + e.getMessage());
        }
    }

    // Método 'synchronized' para asegurar que solo un hilo a la vez pueda escribir en el archivo de texto y evitar que se corrompa
    public synchronized void escribirLog(String evento) {
        // Abre el archivo en modo "append" (true) para añadir texto al final
        try (PrintWriter writer = new PrintWriter(new FileWriter(RUTA_LOG, true))) {
            String timestamp = LocalDateTime.now().format(formatter);
            writer.println("[" + timestamp + "] " + evento);
        } catch (IOException e) {
            System.err.println("Error escribiendo en log: " + e.getMessage());
        }
    }

    // Devuelve a qué zona se va a mover un personaje, asegurando que no sea la misma en la que ya está
    public synchronized String obtenerZonaUpsideDownAleatoria(String zonaActual) {
        // red mental: Si este evento está activo, ignora la aleatoriedad y envía al personaje a la zona con más presas (niños)
        if (redMentalActiva) return getZonaConMasNinos();
        
        // apagón: Si este evento está activo, impide el movimiento (se quedan donde están)
        if (apagonActivo && !zonaActual.isEmpty()) return zonaActual;

        String nuevaZona;
        // Bucle para elegir una zona aleatoria diferente a la actual
        do {
            int index = ThreadLocalRandom.current().nextInt(nombresZonas.length);
            nuevaZona = nombresZonas[index];
        } while (nuevaZona.equals(zonaActual));
        return nuevaZona;
    }

    // Registra la entrada física de un Demogorgon a una lista de zona y lo guarda en el log
    public synchronized void entrarZonaDemogorgon(Demogorgon d, String zona) {
        obtenerListaDemosPorZona(zona).add(d);
        escribirLog("Demogorgon " + d.getIdDemogorgon() + " ha entrado a la zona: " + zona);
    }

    // Borra al Demogorgon de la lista de la zona cuando se mueve a otra
    public synchronized void salirZonaDemogorgon(Demogorgon d, String zona) {
        if (!zona.isEmpty()) {
            obtenerListaDemosPorZona(zona).remove(d);
        }
    }

    // Busca un niño en una zona específica para que el Demogorgon lo ataque
    public synchronized Nino seleccionarObjetivoEnZona(String zona) {
        List<Nino> listaZona = obtenerListaNinosPorZona(zona);
        // Si la zona existe y hay niños dentro
        if (listaZona != null && !listaZona.isEmpty()) {
            // Elige a un niño al azar de esa lista y lo devuelve
            int indexAleatorio = ThreadLocalRandom.current().nextInt(listaZona.size());
            return listaZona.get(indexAleatorio);
        }
        return null; // Si no hay nadie, devuelve null
    }

    // Calcula si un ataque de Demogorgon a un niño tiene éxito
    public synchronized boolean resolverAtaque(Demogorgon d, Nino n) {
        // Genera un número aleatorio entre 0 y 2. Si es 0 (1/3 de probabilidad o 33%), tiene éxito.
        boolean exito = ThreadLocalRandom.current().nextInt(3) == 0;
        if (!exito) {
            escribirLog("El ataque de " + d.getIdDemogorgon() + " sobre " + n.getIdNino() + " ha FRACASADO");
        }
        return exito;
    }

    // Lógica para cuando un niño es capturado por un monstruo
    public synchronized void registrarCaptura(Demogorgon d, Nino n) {
        // Incrementa la variable atómica global de capturas
        int totales = capturasTotalesColmena.incrementAndGet();
        
        // Crea un mensaje formateado y lo envía al log
        String mensaje = String.format(
            "El demogorgon %s ataca al niño %s (capturas propias: %d | Total capturas: %d)", 
            d.getIdDemogorgon(), n.getIdNino(), d.getCapturas(), totales
        );
        escribirLog(mensaje);

        // Si el total de capturas globales es múltiplo de 8, el jefe Vecna crea un nuevo monstruo
        if (totales % 8 == 0) {
            crearNuevoDemogorgon();
        }
    }
    
    // Método privado que instancia y lanza un nuevo hilo Demogorgon
    private void crearNuevoDemogorgon() {
        // Genera un ID con ceros a la izquierda (ej: D0001, D0002) usando un contador atómico
        String nuevoId = String.format("D%04d", contadorIdsDemogorgon.getAndIncrement());
        Demogorgon nuevoD = new Demogorgon(nuevoId, this);
        registrarDemogorgon(nuevoD); // Lo mete en la lista para el ranking
        nuevoD.start(); // Inicia el ciclo de vida (run) del nuevo Demogorgon
        escribirLog("VECNA ha generado un nuevo demogorgon: " + nuevoId);
    }
    
    // Actualizar zonas
    // Método de utilidad (helper) que traduce el String de la zona a la lista de Niños correspondiente
    private List<Nino> obtenerListaNinosPorZona(String zona) {
        switch (zona) {
            case "BOSQUE": return ninosEnBosque;
            case "LABORATORIO": return ninosEnLaboratorio;
            case "CENTRO COMERCIAL": return ninosEnCentroComercial;
            case "ALCANTARILLADO": return ninosEnAlcantarillado;
            default: return null;
        }
    }
    
    // Método de utilidad (helper) que traduce el String de la zona a la lista de Demogorgons correspondiente
    private List<Demogorgon> obtenerListaDemosPorZona(String zona) {
        switch (zona) {
            case "BOSQUE": return demosBosque;
            case "LABORATORIO": return demosLaboratorio;
            case "CENTRO COMERCIAL": return demosCentroComercial;
            case "ALCANTARILLADO": return demosAlcantarillado;
            default: return null;
        }
    }
    
    // Funciones que mueven a los niños de una colección (lista) a otra
    public synchronized void nacerEnCallePrincipal(Nino n) {
        ninosCallePrincipal.add(n);
    }

    public synchronized void entrarCallePrincipalDesdeRadio(Nino n) {
        ninosRadioWsqk.remove(n);
        ninosCallePrincipal.add(n);
    }

    public synchronized void irASotanoByers(Nino n) {
        ninosCallePrincipal.remove(n);
        ninosSotanoByers.add(n);
    }

    // Mueve al niño desde un lugar seguro (sotano) al Upside Down (zona insegura)
    public synchronized void entrarZonaInsegura(Nino n, String zona) {
        ninosSotanoByers.remove(n);
        obtenerListaNinosPorZona(zona).add(n);
    }

    public synchronized void salirZonaInsegura(Nino n, String zona) {
        obtenerListaNinosPorZona(zona).remove(n);
    }

    // Cuando un niño entra a la radio a donar sangre
    public synchronized void entrarRadioWsqk(Nino n) {
        sangreTotal += n.getSangre(); // Suma la sangre del niño a la reserva global
        n.vaciarSangre(); // Deja al niño a 0 de sangre
        ninosRadioWsqk.add(n);
    }

    // Lógica para cuando un niño es capturado y atrapado
    public synchronized void esperarEnColmena(Nino n) throws InterruptedException {
        colmena.add(n);
        escribirLog("El niño " + n.getIdNino() + " ha sido capturado y está en la colmena. " +
                    "Tamaño colmena: " + colmena.size());
        
        // Bucle vital de sincronización: el hilo del niño se suspende (wait) hasta que alguien (Eleven) le cambie el estado de capturado a false y lance un notifyAll()
        while (n.isCapturado()) {
            wait();
        }
        // Si sale del bucle, es porque ha sido salvado, así que se le saca de la colmena
        colmena.remove(n);
    }

    // Devuelve uno de los 4 portales al azar
    public Portal seleccionarPortalAleatorio() {
        int r = ThreadLocalRandom.current().nextInt(4);
        if (r == 0) return portalBosque;
        if (r == 1) return portalLaboratorio;
        if (r == 2) return portalCentroComercial;
        return portalAlcantarillado;
    }
    
    // Control de pausa
    
    // Alterna el estado de pausa. Si se despausa, despierta a todos los hilos que estén durmiendo (wait)
    public synchronized void conmutarPausa() {
        pausado = !pausado;
        if (!pausado) notifyAll(); // despierta a todos los hilos al continuar
    }

    // Los hilos llaman a este método constantemente. Si está pausado, se duermen hasta recibir un notifyAll()
    public synchronized void esperarSiPausado() throws InterruptedException {
        while (pausado) wait();
    }

    // Devuelve el estado de pausa para consultas remotas (RMI)
    @Override
    public synchronized boolean isPausado() throws RemoteException {
        return pausado;
    }
        
    // getters para interfaz gráfica y lógicas internas
    public List<Nino> getNinosCallePrincipal() { return ninosCallePrincipal; }
    public List<Nino> getNinosSotanoByers() { return ninosSotanoByers; }
    public List<Nino> getNinosRadioWsqk() { return ninosRadioWsqk; }
    public List<Nino> getNinosEnZona(String zona) { return obtenerListaNinosPorZona(zona); }
    public List<Demogorgon> getDemogorgonsEnZona(String zona) { return obtenerListaDemosPorZona(zona); }
    public int getSangreTotal() { return sangreTotal; }
    public int getColmenaSize() { return colmena.size(); }
    

    public Portal getPortalBosque() { return portalBosque; }
    public Portal getPortalLaboratorio() { return portalLaboratorio; }
    public Portal getPortalCentroComercial() { return portalCentroComercial; }
    public Portal getPortalAlcantarillado() { return portalAlcantarillado; }
    
    // metodos de eventos globales  
    // Configura qué evento está activo y cuánto va a durar
    public synchronized void iniciarEvento(String tipo, long duracion) {
        
        this.nombreEventoActual = tipo;
        this.tiempoFinEventoMs = System.currentTimeMillis() + duracion; // Calcula el instante futuro exacto en milisegundos en el que acabará
        
        escribirLog(">>> EVENTO GLOBAL INICIADO: " + tipo + " (Duración: " + (duracion/1000) + "s) <<<");
        // Aplica el efecto inicial del evento
        switch(tipo) {
            case "APAGON":
                apagonActivo = true;
                // se bloquean todos los portales (el objeto Portal no dejará pasar a nadie)
                portalBosque.setApagon(true); 
                portalLaboratorio.setApagon(true);
                portalCentroComercial.setApagon(true); 
                portalAlcantarillado.setApagon(true);
                break;
            case "TORMENTA":
                tormentaActiva = true;
                break;
            case "ELEVEN":
                setElevenActiva(true); // Pausa a los demogorgons
                activarPoderEleven(); // Libera a los niños de la colmena
                break;
            case "RED_MENTAL":
                redMentalActiva = true;
                break;
        }
    }

    // Devuelve la normalidad al mundo cuando el tiempo del evento ha concluido
    public synchronized void finalizarEvento(String tipo) {
        escribirLog("<<< EVENTO GLOBAL FINALIZADO: " + tipo + " >>>");
        switch(tipo) {
            case "APAGON":
                apagonActivo = false;
                // se abren todos los portales nuevamente
                portalBosque.setApagon(false);
                portalLaboratorio.setApagon(false);
                portalCentroComercial.setApagon(false);
                portalAlcantarillado.setApagon(false);
                break;
            case "TORMENTA":
                tormentaActiva = false;
                break;
            case "ELEVEN":
                setElevenActiva(false); // Libera a los demogorgons
                break;
            case "RED_MENTAL":
                redMentalActiva = false;
                break;
        }
    }

    // Getter específico para saber si la tormenta está activa (utilizado por el Demogorgon para volverse más agresivo)
    public boolean isTormentaActiva() { return tormentaActiva; }

    //red mental: Lógica para buscar la zona con más niños
    // Compara el tamaño de todas las listas de zonas inseguras y devuelve el nombre de la que tenga mayor número
    private String getZonaConMasNinos() {
        int max = ninosEnBosque.size();
        String zona = "BOSQUE";
        if (ninosEnLaboratorio.size() > max) { max = ninosEnLaboratorio.size(); zona = "LABORATORIO"; }
        if (ninosEnCentroComercial.size() > max) { max = ninosEnCentroComercial.size(); zona = "CENTRO COMERCIAL"; }
        if (ninosEnAlcantarillado.size() > max) { max = ninosEnAlcantarillado.size(); zona = "ALCANTARILLADO"; }
        return zona;
    }

    // eleven: Paralización y liberación
    // Utiliza un monitor específico (monitorEleven) distinto al del mundo para pausar SOLO a los monstruos, no a los niños
    private void setElevenActiva(boolean activa) {
        synchronized (monitorEleven) {
            elevenActiva = activa;
            if (!activa) monitorEleven.notifyAll(); // Despierta a los demogorgons cuando el evento acaba
        }
    }

    // Método que llaman los Demogorgons para comprobar si Eleven los tiene congelados
    public void esperarSiEleven() throws InterruptedException {
        synchronized (monitorEleven) {
            while (elevenActiva) monitorEleven.wait();
        }
    }

    // Lógica para rescatar niños basada en la sangre recolectada
    private void activarPoderEleven() {
        // Calcula cuántos niños se pueden salvar. Toma el mínimo entre la sangre total disponible y el número de niños en la colmena
        int aLiberar = Math.min(sangreTotal, colmena.size());
        escribirLog("Eleven usa sus poderes: " + aLiberar + " niños liberados. Sangre consumida: " + aLiberar);
        sangreTotal -= aLiberar; // Descuenta la sangre gastada

        // Desmarca como "capturados" a la cantidad de niños correspondientes
        for (int i = 0; i < aLiberar; i++) {
            Nino salvado = colmena.get(i);
            salvado.setCapturado(false);
        }
        // Despierta a todos los hilos suspendidos (especialmente a los niños que estaban en el wait() de esperarEnColmena)
        notifyAll();
    }
    
    // Funciones RMI que la interfaz gráfica invoca periódicamente para actualizar la pantalla
    
    // Suma la cantidad total de niños en zonas seguras de Hawkins
    @Override
    public int getTotalNinosHawkins() {
        return ninosCallePrincipal.size() + ninosSotanoByers.size() + ninosRadioWsqk.size();
    }
    
    
    // Genera un texto en formato HTML con el número de niños que hay actualmente dentro de cada portal
    @Override
    public String getPortalesHTML() throws RemoteException {

        return "Portal 1 [" + portalBosque.getNinosCruzando() + "] niños<br><br>" +
               "Portal 2 [" + portalLaboratorio.getNinosCruzando() + "] niños<br><br>" +
               "Portal 3 [" + portalCentroComercial.getNinosCruzando() + "] niños<br><br>" +
               "Portal 4 [" + portalAlcantarillado.getNinosCruzando() + "] niños";
    }

    // Genera texto HTML con el recuento de presas por zona
    @Override
    public String getUbicacionesNinosHTML() throws RemoteException {
        return "Bosque(" + ninosEnBosque.size() + ")<br><br>" +
               "Laboratorio(" + ninosEnLaboratorio.size() + ")<br><br>" +
               "C. Comercial(" + ninosEnCentroComercial.size() + ")<br><br>" +
               "Alcantarillado(" + ninosEnAlcantarillado.size() + ")";
    }

    // Genera texto HTML con el recuento de monstruos por zona
    @Override
    public String getUbicacionesDemosHTML() throws RemoteException {
        return "Bosque(" + demosBosque.size() + ")<br><br>" +
               "Laboratorio(" + demosLaboratorio.size() + ")<br><br>" +
               "C. Comercial(" + demosCentroComercial.size() + ")<br><br>" +
               "Alcantarillado(" + demosAlcantarillado.size() + ")";
    }
    
    // Añade el monstruo a la lista maestra para poder generar el ranking
    public synchronized void registrarDemogorgon(Demogorgon d) {
        listaDemogorgons.add(d);
    }

    // metodo mri
    // Calcula el "Top 3" de Demogorgons que más niños han cazado
    @Override
    public synchronized String getRankingDemos() throws java.rmi.RemoteException {
        // ordenamos la lista de mayor a menor número de capturas usando una expresión Lambda para comparar
        listaDemogorgons.sort((d1, d2) -> Integer.compare(d2.getCapturas(), d1.getCapturas()));
        
        StringBuilder sb = new StringBuilder();
        // Recorre la lista ordenada, tomando solo hasta un máximo de los 3 primeros
        for (int i = 0; i < Math.min(3, listaDemogorgons.size()); i++) {
            Demogorgon d = listaDemogorgons.get(i);
            sb.append((i + 1)).append(". ").append(d.getIdDemogorgon())
              .append(" [").append(d.getCapturas()).append("]\n");
        }
        
        // Si no hay ninguno aún, devuelve la estructura vacía
        if(sb.length() == 0) return "1. \n2. \n3. ";
        return sb.toString();
    }

    // Calcula y devuelve cuánto tiempo queda para que acabe el evento en curso
    @Override
    public synchronized String getEstadoEvento() throws java.rmi.RemoteException {
        // Resta el tiempo guardado como límite final al tiempo actual de la máquina
        long milisegundosRestantes = tiempoFinEventoMs - System.currentTimeMillis();
        
        // Si ya ha pasado el tiempo (o no hay evento)
        if (milisegundosRestantes <= 0) {
            return "Tipo: Ninguno\nTiempo restante: 0s";
        }
        
        // Convierte milisegundos a segundos para presentarlo bonito al usuario
        long segundos = milisegundosRestantes / 1000;
        return "Tipo: " + nombreEventoActual + "\nTiempo restante: " + segundos + "s";
    }
}