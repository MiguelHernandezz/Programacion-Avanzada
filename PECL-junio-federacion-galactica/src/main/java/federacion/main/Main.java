package federacion.main;

import federacion.hilos.GeneradorActores;
import federacion.hilos.OrquestadorRefuerzos;
import federacion.modelo.TipoRecurso;
import federacion.monitores.BaseSaqueadores;
import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
import federacion.rmi.FederacionImpl;
import federacion.rmi.FederacionRemota;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Arrays;
import java.util.List;

// La clase Main es el punto de arranque de toda la simulación.
// Su responsabilidad arquitectónica es instanciar el "tablero de juego" (los Monitores) de forma puramente secuencial
// ANTES de crear ningún hilo. Esto garantiza que no haya condiciones de carrera durante el arranque del sistema.
public class Main {

    public static void main(String[] args) {

        // ==========================================
        // 1. INICIALIZACIÓN DEL REGISTRO (LOG)
        // ==========================================
        // Es el primer monitor en nacer porque todos los demás objetos (planetas, depósitos, hilos) 
        // lo necesitan inyectado en sus constructores para poder escribir eventos de forma concurrente y segura.
        GestorLog log = new GestorLog();
        log.registrarEvento("Iniciando sistema de la federacion galactica...");

        // ==========================================
        // 2. CREACIÓN DE PLANETAS MINEROS (Monitores)
        // ==========================================
        // Se instancian las 5 zonas críticas independientes de extracción. Cada planeta tendrá
        // su propio cerrojo (lock) interno, lo que permite que varios hilos interactúen en planetas distintos a la vez sin bloquearse entre sí.
        Planeta cryon  = new Planeta("Cryon",  TipoRecurso.CRISTAL, log);
        Planeta velora = new Planeta("Velora", TipoRecurso.CRISTAL, log);
        Planeta ferrum = new Planeta("Ferrum", TipoRecurso.MINERAL, log);
        Planeta drax   = new Planeta("Drax",   TipoRecurso.MINERAL, log);
        Planeta ignis  = new Planeta("Ignis",  TipoRecurso.PLASMA,  log);
        // Se agrupan en una colección inmutable para facilitar su paso a los hilos viajeros
        List<Planeta> planetas = Arrays.asList(cryon, velora, ferrum, drax, ignis);

        // ==========================================
        // 3. CREACIÓN DE DEPÓSITOS (Monitores)
        // ==========================================
        // Se instancian las 3 zonas críticas de almacenamiento. Al igual que los planetas, 
        // son espacios de memoria compartida donde los hilos (delegados y patrullas) tendrán que sincronizarse.
        Deposito depCristal = new Deposito("Deposito de Cristal", TipoRecurso.CRISTAL, 250, 100, log);
        Deposito depMineral = new Deposito("Deposito de Mineral", TipoRecurso.MINERAL, 200,  80, log);
        Deposito depPlasma  = new Deposito("Deposito de Plasma",  TipoRecurso.PLASMA,  150,  60, log);
        List<Deposito> depositos = Arrays.asList(depCristal, depMineral, depPlasma);

        // ==========================================
        // 4. ZONAS COMUNES DE COORDINACIÓN
        // ==========================================
        // Monitores de gestión global. El Sistema Central rastrea dónde está cada actor, 
        // y la Base de Saqueadores orquesta los ataques para evitar que dos enemigos vayan al mismo sitio.
        SistemaCentral sistemaCentral = new SistemaCentral(log);
        BaseSaqueadores baseSaqueadores = new BaseSaqueadores(planetas, depositos, log);

        // ==========================================
        // 5. ARRANQUE DEL ORQUESTADOR DE REFUERZOS
        // ==========================================
        // Se crea y se da vida al primer hilo real del sistema. Este hilo demonio vigilará los depósitos
        // en segundo plano para construir nuevas patrullas cuando la economía lo permita.
        OrquestadorRefuerzos orquestador = new OrquestadorRefuerzos(2, depositos, planetas, sistemaCentral, log);
        orquestador.start();

        // ==========================================
        // 6. ARRANQUE DEL GENERADOR DE ACTORES
        // ==========================================
        // Se inicializa la "fábrica" de hilos. Al llamar a iniciarGeneracion(), este objeto
        // creará internamente varios hilos creadores que empezarán a poblar la simulación con delegados, patrullas y saqueadores.
        GeneradorActores generador = new GeneradorActores(sistemaCentral, baseSaqueadores, planetas, depositos, log);
        generador.iniciarGeneracion();

        // ==========================================
        // 7. CONFIGURACIÓN DEL SERVIDOR RMI
        // ==========================================
        // Este bloque expone los monitores del sistema al exterior, permitiendo que un programa cliente 
        // en otro ordenador (o en otra ventana) pueda consultar variables y ejecutar métodos remotos.
        try {
            // Instancia el objeto remoto pasándole las referencias de los monitores reales
            FederacionRemota stub = new FederacionImpl(planetas, depositos, sistemaCentral, baseSaqueadores, orquestador);
            // Crea un registro RMI en el puerto estándar (1099) para que los clientes sepan dónde conectarse
            Registry registry = LocateRegistry.createRegistry(1099); 
            // Publica el objeto remoto bajo el nombre "FederacionGalactica"
            registry.rebind("FederacionGalactica", stub);
            log.registrarEvento("Servidor RMI iniciado en el puerto 1099.");
        } catch (Exception e) {
            System.err.println("Error al iniciar el servidor RMI: " + e.getMessage());
            e.printStackTrace();
        }

        log.registrarEvento("Todos los monitores e hilos inicializados correctamente.");

        // ==========================================
        // 8. DESPLIEGUE DE LA INTERFAZ GRÁFICA (GUI)
        // ==========================================
        // Regla de oro en Java Swing: La interfaz gráfica nunca debe crearse ni modificarse desde el hilo principal (Main Thread).
        // EventQueue.invokeLater() delega la creación de la ventana al "Event Dispatch Thread" (EDT), 
        // un hilo exclusivo de Java para pintar botones y paneles de forma segura sin colgarse.
        java.awt.EventQueue.invokeLater(() -> {
            // Se le inyectan los monitores a la interfaz para que sus "Timers" puedan leer el estado del mundo en tiempo real
            federacion.gui.Interfaz_Principal gui = new federacion.gui.Interfaz_Principal(
                    planetas, depositos, sistemaCentral, baseSaqueadores, orquestador);
            // Se configura el Patrón Observer: Cada vez que el GestorLog registre algo internamente,
            // ejecutará el método 'agregarLog' de la interfaz gráfica para actualizar el panel de texto al instante.
            log.setObservadorUi(gui::agregarLog);
            // Hace visible la ventana del servidor
            gui.setVisible(true);
        });
    }
}