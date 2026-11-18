package federacion.hilos;

import federacion.modelo.TipoRecurso;
import federacion.monitores.Deposito;
import federacion.monitores.GestorLog;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
import java.util.List;

// La clase OrquestadorRefuerzos hereda de Thread, por lo que actuará como un proceso independiente.
// Su trabajo es vigilar constantemente los depósitos y fabricar nuevas patrullas cuando haya suficientes materiales.
public class OrquestadorRefuerzos extends Thread {

    // Referencias directas a los tres depósitos específicos que necesita para fabricar naves
    private final Deposito depCristal;
    private final Deposito depMineral;
    private final Deposito depPlasma;
    // Listas generales del mundo para podérselas pasar a las nuevas patrullas que fabrique
    private final List<Planeta> planetas;
    private final List<Deposito> depositos;
    // Gestores centrales para registrar la actividad
    private final SistemaCentral sistemaCentral;
    private final GestorLog log;
    
    // Cuántas patrullas se han fabricado hasta el momento en toda la simulación
    private int contadorPatrullasTotales;
    // Límite máximo de patrullas que la Federación permite tener activas
    private final int MAX_PATRULLAS = 20;

    // Bandera para saber si en este preciso instante la fábrica está trabajando en ensamblar una nave
    private boolean estadoActivo = false;

    // Constructor para inicializar al orquestador con el estado actual del mundo
    public OrquestadorRefuerzos(int patrullasIniciales, List<Deposito> depositos, List<Planeta> planetas, 
                                SistemaCentral sistemaCentral, GestorLog log) {
        super("OrquestadorRefuerzos");
        // Al configurarlo como "Daemon" (demonio), le decimos al programa que este hilo es un trabajador de fondo.
        // Si el programa principal se cierra, este hilo morirá automáticamente sin impedir que la aplicación termine.
        this.setDaemon(true); 
        this.contadorPatrullasTotales = patrullasIniciales;
        this.planetas = planetas;
        this.depositos = depositos;
        this.sistemaCentral = sistemaCentral;
        this.log = log;

        // Mapeamos los depósitos buscando en la lista general cuál es cuál para tener un acceso rápido
        // y no tener que buscarlos cada vez que queramos comprobar si hay materiales
        this.depCristal = depositos.stream().filter(d -> d.getTipoRecurso() == TipoRecurso.CRISTAL).findFirst().orElseThrow();
        this.depMineral = depositos.stream().filter(d -> d.getTipoRecurso() == TipoRecurso.MINERAL).findFirst().orElseThrow();
        this.depPlasma = depositos.stream().filter(d -> d.getTipoRecurso() == TipoRecurso.PLASMA).findFirst().orElseThrow();
    }
    
    // Getters para que la interfaz gráfica pueda consultar el progreso de la fábrica
    public int getContadorPatrullasTotales() {
        return contadorPatrullasTotales;
    }

    public boolean isEstadoActivo() {
        return estadoActivo;
    }

    // El método run() contiene el ciclo de vida del orquestador (su rutina de trabajo)
    @Override
    public void run() {
        // Bucle de trabajo: seguirá operando mientras no se haya alcanzado el límite máximo de 20 patrullas
        while (contadorPatrullasTotales < MAX_PATRULLAS) { 
            try {
                // Primero hace una comprobación visual rápida. Mira si hay suficientes materiales en los tres depósitos
                // a la vez, sin bloquear a los delegados que estén depositando en ese momento.
                if (depCristal.getCantidadAlmacenada() >= 150 && 
                    depMineral.getCantidadAlmacenada() >= 100 && 
                    depPlasma.getCantidadAlmacenada() >= 75) { 

                    // Si parece que hay materiales, intenta retirarlos de verdad, uno por uno.
                    // Si logra sacar el cristal...
                    if (depCristal.consumirRecursos(150)) {
                        // ...intenta sacar el mineral...
                        if (depMineral.consumirRecursos(100)) {
                            // ...y finalmente intenta sacar el plasma.
                            if (depPlasma.consumirRecursos(75)) {
                                
                                // ¡Éxito! Ha conseguido reunir todos los materiales necesarios.
                                // 1. Enciende la luz de "Fábrica Trabajando" (estadoActivo a true)
                                estadoActivo = true;
                                
                                // Prepara la matrícula de la nueva patrulla (ej. P003)
                                contadorPatrullasTotales++;
                                String idNueva = String.format("P%03d", contadorPatrullasTotales); 
                                log.registrarEvento("REFUERZOS AUTOMÁTICOS: Se crea la patrulla " + idNueva + ".");
                                
                                // 2. Simula el tiempo que tardan los ingenieros en ensamblar la nave (3 segundos)
                                Thread.sleep(3000);
                                
                                // 3. La nave está lista. Crea a la nueva patrulla y le da vida con .start()
                                Patrulla nuevaPatrulla = new Patrulla(idNueva, sistemaCentral, planetas, depositos, log); 
                                nuevaPatrulla.start(); 
                                log.registrarEvento("REFUERZOS AUTOMÁTICOS: La patrulla " + idNueva + " ha sido desplegada.");
                                
                                // 4. Apaga la luz de la fábrica, indicando que ha terminado este encargo
                                estadoActivo = false;
                                
                            } else {
                                // Falló al intentar coger el plasma (quizás un saqueador lo robó justo en este instante).
                                // Como no puede fabricar la nave, actúa con responsabilidad y devuelve el mineral y el cristal
                                // que ya tenía en las manos a sus respectivos depósitos para no desperdiciarlos.
                                depMineral.devolverRecursos(100);
                                depCristal.devolverRecursos(150);
                            }
                        } else {
                            // Falló al intentar coger el mineral. Devuelve el cristal que ya había cogido.
                            depCristal.devolverRecursos(150);
                        }
                    }
                }
                // Tras revisar los almacenes (y fabricar o no), el orquestador descansa 1 segundo.
                // Esto evita que esté preguntando a los depósitos a la velocidad de la luz y sature el ordenador.
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                // Si alguien interrumpe o apaga el sistema, el orquestador rompe su ciclo de trabajo y termina tranquilamente.
                break;
            }
        }
    }
}