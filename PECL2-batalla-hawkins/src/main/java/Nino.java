// Importación para generar números aleatorios de forma eficiente en entornos con múltiples hilos
import java.util.concurrent.ThreadLocalRandom;

// La clase Nino hereda de Thread, por lo que cada niño actuará como un proceso independiente (concurrente)
public class Nino extends Thread {
    
    // Identificador único del niño (ej. N001, N002)
    private final String idNino;
    // Referencia al gestor central para interactuar con las zonas y otros elementos del juego
    private final GestorMundo mundo;
    // Cantidad de sangre que el niño lleva recolectada en su inventario
    private int sangreRecogida = 0;
    // Bandera para saber si está atrapado en el Upside Down
    private boolean capturado = false;
    // Bandera para saber si en este preciso momento está forcejeando con un monstruo
    private boolean bajoAtaque = false;
    
    // Objeto que actúa como "candado" (monitor) para evitar que dos demogorgons lo ataquen al mismo tiempo
    private final Object monitorAtaque = new Object();

    // Constructor para inicializar al niño con su ID y el mundo en el que interactúa
    public Nino(String idNino, GestorMundo mundo) {
        this.idNino = idNino;
        this.mundo = mundo;
    }

    // El método run() contiene todo el ciclo de vida y comportamiento del niño
    @Override
public void run() {
    try {
        // Al iniciar, el niño aparece por primera vez en la Calle Principal (zona segura)
        mundo.nacerEnCallePrincipal(this);
        
        // Bucle infinito: el niño repetirá este ciclo hasta que se cierre el programa
        while (true) {
            mundo.esperarSiPausado(); // Verifica si el mundo está en pausa
            
            // El niño va al Sótano de Byers (zona de preparación antes de entrar a un portal)
            mundo.irASotanoByers(this);
            // Simula el tiempo que pasa en el sótano preparándose (entre 1 y 2 segundos)
            Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 2001));

            mundo.esperarSiPausado();
            
            // Selecciona un portal aleatorio para viajar al Upside Down
            Portal p = mundo.seleccionarPortalAleatorio();
            // Se pone en cola y espera a que haya suficientes niños para cruzar en grupo
            p.esperarGrupoYCruzar(this);
            // Simula el tiempo físico que tarda en cruzar el portal (1 segundo)
            Thread.sleep(1000);
            mundo.esperarSiPausado();
            // Libera su plaza en el portal una vez ha cruzado
            p.finalizarCruceIda(this);

            // Determina a qué zona peligrosa del Upside Down ha llegado a través del portal
            String zona = p.getNombreDestino();
            // Notifica al mundo que ha entrado a esta zona, exponiéndose a los Demogorgons
            mundo.entrarZonaInsegura(this, zona);

            // Calcula el tiempo que invertirá recolectando sangre (de 3 a 5 segundos)
            long tiempoRecoleccion = ThreadLocalRandom.current().nextLong(3000, 5001);
            // Si el evento de Tormenta está activo, la recolección es más lenta (tarda el doble)
            if (mundo.isTormentaActiva()) {
                tiempoRecoleccion *= 2;
            }
            // Simula el tiempo que pasa recolectando
            Thread.sleep(tiempoRecoleccion);

            mundo.esperarSiPausado();
            // Incrementa su inventario de sangre
            sangreRecogida++;
            
            // ¡Punto crítico! Antes de poder irse, comprueba si está siendo atacado en este preciso instante. 
            // Si es así, se queda bloqueado aquí esperando a que se resuelva el combate.
            esperarSiBajoAtaque();
            // Si no estaba siendo atacado (o sobrevivió al ataque), sale de la zona insegura
            mundo.salirZonaInsegura(this, zona);

            // Si el niño NO fue capturado durante su estadía en la zona
            if (!capturado) {
                // Inicia el cruce de vuelta al mundo normal
                p.cruzarHaciaHawkins(this);
                Thread.sleep(1000); // Tiempo de cruce
                mundo.esperarSiPausado();
                p.finalizarCruceVuelta(this); // Libera su plaza en la vuelta

                // Va a la estación de Radio a donar la sangre recolectada
                mundo.entrarRadioWsqk(this);
                // Simula el tiempo que tarda donando la sangre (de 2 a 4 segundos)
                Thread.sleep(ThreadLocalRandom.current().nextLong(2000, 4001));
                mundo.esperarSiPausado();
                
                // Vuelve a la calle principal
                mundo.entrarCallePrincipalDesdeRadio(this);
                // Descansa en la calle principal (de 3 a 5 segundos) antes de volver a empezar el ciclo
                Thread.sleep(ThreadLocalRandom.current().nextLong(3000, 5001));
                mundo.esperarSiPausado();

            // Si el niño SÍ fue capturado por un Demogorgon
            } else {
                // Es llevado a la colmena. Este método suspenderá (wait) este hilo de forma indefinida 
                // hasta que Eleven lo rescate.
                mundo.esperarEnColmena(this);
                
                // Una vez rescatado, vuelve a aparecer sano y salvo en la Calle Principal
                mundo.nacerEnCallePrincipal(this);
                // Tiempo de recuperación tras el trauma de ser capturado
                Thread.sleep(ThreadLocalRandom.current().nextLong(1000, 2001));
                mundo.esperarSiPausado();
            }
        }
    } catch (InterruptedException e) {
        // Se captura si el programa se interrumpe o cierra
        System.out.println("Niño " + idNino + " finalizado.");
    }
}
    
    // Método que llama el Demogorgon para intentar iniciar un ataque contra este niño
    public boolean intentarSerAtacado() {
        // Se sincroniza con el monitorAtaque para evitar condiciones de carrera
        synchronized (monitorAtaque) { 
            // Si ya está siendo atacado por otro monstruo o ya ha sido capturado, falla el intento de ataque
            if (bajoAtaque || capturado) {
                return false;
            }
            // Si está libre, se marca como "bajo ataque" bloqueando que otros puedan atacarle
            bajoAtaque = true; 
            return true;
        }
    }

    // Método que llama el Demogorgon al terminar el tiempo de ataque para dar el veredicto
    public void finAtaque(boolean fueCapturado) {
        synchronized (monitorAtaque) {
            // Ya no está bajo ataque (ya terminó la pelea)
            this.bajoAtaque = false;
            // Se establece si el monstruo ganó (true) o si el niño logró huir (false)
            this.capturado = fueCapturado;
            // Despierta al hilo del niño (que probablemente estaba en esperarSiBajoAtaque()) para que continúe su código
            monitorAtaque.notifyAll();
        }
    }

    // Método de uso interno que pausa al niño si un Demogorgon lo interceptó
    private void esperarSiBajoAtaque() throws InterruptedException {
        synchronized (monitorAtaque) {
            // Mientras esté en combate, el niño duerme (wait) esperando a que el monstruo llame a finAtaque()
            while (bajoAtaque) { 
                monitorAtaque.wait(); 
            }
        }
    }

    // getters y setters estándar para consultar el estado interno del niño
    
    public String getIdNino() { return idNino; }
    
    public boolean isCapturado() { return capturado; }
    public void setCapturado(boolean capturado) { this.capturado = capturado; }
    public boolean isBajoAtaque() { return bajoAtaque; }

    public int getSangre() { return sangreRecogida; }
    public void vaciarSangre() { this.sangreRecogida = 0; }
}