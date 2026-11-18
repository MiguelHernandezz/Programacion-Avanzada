package federacion.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;

// Esta interfaz actúa como el "Protocolo de Comunicaciones" oficial de la Federación.
// Al heredar de la clase especial 'Remote', le estamos diciendo a la Máquina Virtual Java 
// que los métodos definidos aquí van a ser llamados desde otro ordenador a través de internet (o red local).
// Esta clase no contiene código funcional, solo establece el "contrato" estricto que el Servidor 
// debe cumplir y que el Cliente remoto puede utilizar.
public interface FederacionRemota extends Remote {

    // ==========================================
    // LECTURA DE ECONOMÍA GLOBAL
    // ==========================================
    // Todos los métodos de una interfaz remota están obligados por Java a declarar 'throws RemoteException'.
    // Esto es un escudo arquitectónico: si el cable de red se desconecta, el servidor se apaga
    // o hay mucho lag en la red durante la llamada, Java lanzará esta excepción para evitar que el programa cliente se quede colgado para siempre.
    
    // Recursos almacenados físicamente en tiempo real
    int getCristalAlmacenado() throws RemoteException;
    int getMineralAlmacenado() throws RemoteException;
    int getPlasmaAlmacenado() throws RemoteException;
    
    // Capacidades de las instalaciones (útiles para que la UI dibuje las barras de porcentaje)
    int getCapacidadMaximaCristal() throws RemoteException;
    int getCapacidadMaximaMineral() throws RemoteException;
    int getCapacidadMaximaPlasma() throws RemoteException;

    // ==========================================
    // ESTADO MILITAR
    // ==========================================
    // Consulta al orquestador cuántas naves de combate ha fabricado en total
    int getTotalPatrullas() throws RemoteException;

    // ==========================================
    // RASTREO GPS: DELEGADOS (Naves Mineras)
    // ==========================================
    // Trabajadores descansando/preparándose
    int getDelegadosEnCentro() throws RemoteException;

    // Delegados extrayendo recursos en zonas de alto riesgo
    int getDelegadosCryon() throws RemoteException;
    int getDelegadosVelora() throws RemoteException;
    int getDelegadosFerrum() throws RemoteException;
    int getDelegadosDrax() throws RemoteException;
    int getDelegadosIgnis() throws RemoteException;

    // Delegados haciendo cola o descargando en zonas seguras
    int getDelegadosDepCristal() throws RemoteException;
    int getDelegadosDepMineral() throws RemoteException;
    int getDelegadosDepPlasma() throws RemoteException;

    // ==========================================
    // RASTREO GPS: PATRULLAS (Naves Defensivas)
    // ==========================================
    // Patrullas defendiendo la órbita de los planetas
    int getPatrullasCryon() throws RemoteException;
    int getPatrullasVelora() throws RemoteException;
    int getPatrullasFerrum() throws RemoteException;
    int getPatrullasDrax() throws RemoteException;
    int getPatrullasIgnis() throws RemoteException;

    // Patrullas custodiando las bóvedas de los depósitos
    int getPatrullasDepCristal() throws RemoteException;
    int getPatrullasDepMineral() throws RemoteException;
    int getPatrullasDepPlasma() throws RemoteException;

    // ==========================================
    // ZONAS MÉDICAS Y ENEMIGOS
    // ==========================================
    // Naves aliadas (tanto civiles como militares) que están siendo reparadas tras sufrir ataques
    int getDelegadosEnRecuperacion() throws RemoteException;
    int getPatrullasEnRecuperacion() throws RemoteException;

    // Radar enemigo: piratas esparando en su cuartel general
    int getSaqueadoresEnBase() throws RemoteException;

    // ==========================================
    // COMANDOS DE ADMINISTRADOR (Escritura)
    // ==========================================
    // A diferencia del resto de la interfaz (que es de solo lectura), estos comandos 
    // permiten al cliente intervenir directamente en la simulación, borrando el progreso de los depósitos
    // con un solo clic de forma remota.
    void vaciarDepositoCristal() throws RemoteException;
    void vaciarDepositoMineral() throws RemoteException;
    void vaciarDepositoPlasma() throws RemoteException;
}