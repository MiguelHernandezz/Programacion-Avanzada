// Importaciones necesarias para generación de números aleatorios y configuración de red (RMI)
import java.util.Random;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;

// Clase principal que actúa como punto de entrada (Main) del servidor de la simulación
public class ServidorHawkins {
    
    // Método principal que lanza la aplicación, puede arrojar excepciones de red o de interrupción de hilos
    public static void main(String[] args) throws RemoteException, InterruptedException {
        
        // 1. INICIALIZACIÓN DEL NÚCLEO
        // Se crea la instancia única del GestorMundo, que será compartida por todos los hilos y clientes remotos
        GestorMundo mundo = new GestorMundo();
        
        // 2. INTERFAZ GRÁFICA LOCAL (SERVIDOR)
        // Se lanza la ventana de la interfaz del servidor de forma segura usando el hilo de despacho de eventos de Swing
        javax.swing.SwingUtilities.invokeLater(() -> {
            InterfazHawkings ui = new InterfazHawkings(mundo);
            ui.setVisible(true); // Hace visible la ventana
        });

        System.out.println("Iniciando simulación de La Batalla de Hawkins...");

        // 3. CONFIGURACIÓN DEL SERVICIO RMI (CLIENTE-SERVIDOR)
        // Define la dirección IP del servidor para RMI (en este caso, la máquina local)
        System.setProperty("java.rmi.server.hostname", "localhost");
        try {
            // Crea e inicia el registro RMI en el puerto estándar 1099
            LocateRegistry.createRegistry(1099);
            // Publica (o sobrescribe si ya existe) el objeto 'mundo' con el nombre "HawkinsService"
            // A partir de este momento, los clientes (como ClienteRemoto) pueden conectarse a él
            Naming.rebind("HawkinsService", mundo);
            System.out.println("Servidor RMI listo en localhost");
        } catch (Exception e) {
            // Captura e imprime cualquier error al intentar levantar el servidor RMI (ej. puerto ocupado)
            e.printStackTrace();
        }

        // 4. INICIALIZACIÓN DE ENTIDADES
        // Se crea al Demogorgon original (el "Alfa") con el ID D0000
        Demogorgon alpha = new Demogorgon("D0000", mundo);
        // Se añade al gestor para que conste en los rankings y controles
        mundo.registrarDemogorgon(alpha);
        // Inicia el hilo del Demogorgon Alfa para que empiece a cazar
        alpha.start();

        // 5. INICIALIZACIÓN DE EVENTOS GLOBALES
        // Se crea e inicia el hilo controlador que lanzará apagones, tormentas, etc., de forma periódica
        ControladorEventos eventos = new ControladorEventos(mundo);
        eventos.start();

        // 6. GENERADOR ESCALONADO DE NIÑOS
        // Se crea un hilo dedicado exclusivamente a instanciar y lanzar a los 1500 niños poco a poco
        Thread generador = new Thread(() -> {
            Random random = new Random();
            // Bucle para crear exactamente 1500 niños
            for (int i = 1; i <= 1500; i++) {
                try {
                    // Respeta la pausa global: si el juego está pausado, deja de generar niños temporalmente
                    mundo.esperarSiPausado();
                    
                    // Formatea el ID del niño con ceros a la izquierda (ej. N0001, N0002... N1500)
                    String idNino = String.format("N%04d", i);
                    // Crea la instancia del niño pasándole su ID y la referencia al mundo
                    Nino nino = new Nino(idNino, mundo);
                    // Inicia el hilo del niño para que empiece su ciclo de vida
                    nino.start();
                    
                    // Espera un tiempo aleatorio entre 500ms y 2000ms (500 + de 0 a 1500) antes de generar al siguiente
                    int tiempoEspera = 500 + random.nextInt(1501);
                    Thread.sleep(tiempoEspera);
                    
                } catch (InterruptedException e) {
                    // Si el hilo generador es interrumpido forzosamente, se detiene la creación de niños
                    System.err.println("Error en la generación escalonada: " + e.getMessage());
                    Thread.currentThread().interrupt(); // Restaura la bandera de interrupción
                    break; // Sale del bucle
                }
            }
            // Mensaje que avisa cuando ya no se van a generar más personajes
            System.out.println("Todos los niños han sido generados.");
        });
        
        // Se configura como hilo "Demonio" (Daemon). Esto significa que si se cierran todas las ventanas
        // y finalizan los demás hilos principales, este hilo no impedirá que la Máquina Virtual de Java se apague.
        generador.setDaemon(true);
        // Arranca el hilo generador
        generador.start();
    }
}