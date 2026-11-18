package federacion.rmi;

import federacion.hilos.OrquestadorRefuerzos;
import federacion.monitores.BaseSaqueadores;
import federacion.monitores.Deposito;
import federacion.monitores.Planeta;
import federacion.monitores.SistemaCentral;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;

// La clase FederacionImpl es el "corazón" de la conexión en red de tu simulación.
// Al heredar de UnicastRemoteObject, Java configura automáticamente los sockets de red por debajo 
// para que este objeto quede "expuesto" escuchando peticiones en un puerto.
// Al implementar FederacionRemota, firma un contrato de los métodos que los clientes de fuera pueden usar.
public class FederacionImpl extends UnicastRemoteObject implements FederacionRemota {

    // Referencias a la memoria física y real del servidor.
    // Este objeto RMI necesita conocer los monitores locales para poder consultarles
    // los datos cada vez que un cliente externo haga una pregunta por la red.
    private final List<Planeta> planetas;       
    private final List<Deposito> depositos;     
    private final SistemaCentral sistemaCentral;
    private final BaseSaqueadores baseSaqueadores;
    private final OrquestadorRefuerzos orquestador;

    // El constructor lanza RemoteException obligatoriamente porque al invocar a super(),
    // Java intenta abrir puertos de red en el Sistema Operativo, lo cual puede fallar (ej. firewall o puerto ocupado).
    public FederacionImpl(List<Planeta> planetas, List<Deposito> depositos,
                          SistemaCentral sistemaCentral, BaseSaqueadores baseSaqueadores,
                          OrquestadorRefuerzos orquestador) throws RemoteException {
        super();
        this.planetas = planetas;
        this.depositos = depositos;
        this.sistemaCentral = sistemaCentral;
        this.baseSaqueadores = baseSaqueadores;
        this.orquestador = orquestador;
    }

    // ==========================================
    // LECTURAS DESDE LA RED (Métodos Puente)
    // ==========================================
    // IMPORTANTE EN CONCURRENCIA: Cuando un cliente llama a estos métodos por la red, 
    // el servidor RMI crea un hilo nuevo (Thread) silenciosamente para atender esa petición.
    // Como las peticiones llaman directamente a métodos 'synchronized' de nuestros Monitores (ej. getCantidadAlmacenada),
    // el sistema es 100% Thread-Safe y evita lecturas corruptas incluso si 50 clientes consultan a la vez.

    // recursos almacenados
    @Override public int getCristalAlmacenado() throws RemoteException { return depositos.get(0).getCantidadAlmacenada(); }
    @Override public int getMineralAlmacenado() throws RemoteException { return depositos.get(1).getCantidadAlmacenada(); }
    @Override public int getPlasmaAlmacenado()  throws RemoteException { return depositos.get(2).getCantidadAlmacenada(); }
    @Override public int getCapacidadMaximaCristal() throws RemoteException { return depositos.get(0).getCapacidadMaxima(); }
    @Override public int getCapacidadMaximaMineral() throws RemoteException { return depositos.get(1).getCapacidadMaxima(); }
    @Override public int getCapacidadMaximaPlasma()  throws RemoteException { return depositos.get(2).getCapacidadMaxima(); }

    // patrullas totales
    @Override public int getTotalPatrullas() throws RemoteException { return orquestador.getContadorPatrullasTotales(); }

    // delegados en el centro
    @Override public int getDelegadosEnCentro() throws RemoteException { return sistemaCentral.getDelegadosEnCentro(); }

    // delegados en planetas
    @Override public int getDelegadosCryon()  throws RemoteException { return planetas.get(0).getDelegadosPresentes(); }
    @Override public int getDelegadosVelora() throws RemoteException { return planetas.get(1).getDelegadosPresentes(); }
    @Override public int getDelegadosFerrum() throws RemoteException { return planetas.get(2).getDelegadosPresentes(); }
    @Override public int getDelegadosDrax()   throws RemoteException { return planetas.get(3).getDelegadosPresentes(); }
    @Override public int getDelegadosIgnis()  throws RemoteException { return planetas.get(4).getDelegadosPresentes(); }

    // delegados en depositos
    @Override public int getDelegadosDepCristal() throws RemoteException { return depositos.get(0).getDelegadosPresentes(); }
    @Override public int getDelegadosDepMineral() throws RemoteException { return depositos.get(1).getDelegadosPresentes(); }
    @Override public int getDelegadosDepPlasma()  throws RemoteException { return depositos.get(2).getDelegadosPresentes(); }

    // patrullas en planeta
    @Override public int getPatrullasCryon()  throws RemoteException { return planetas.get(0).getPatrullasPresentes(); }
    @Override public int getPatrullasVelora() throws RemoteException { return planetas.get(1).getPatrullasPresentes(); }
    @Override public int getPatrullasFerrum() throws RemoteException { return planetas.get(2).getPatrullasPresentes(); }
    @Override public int getPatrullasDrax()   throws RemoteException { return planetas.get(3).getPatrullasPresentes(); }
    @Override public int getPatrullasIgnis()  throws RemoteException { return planetas.get(4).getPatrullasPresentes(); }

    // Patrullas en depositos
    @Override public int getPatrullasDepCristal() throws RemoteException { return depositos.get(0).getPatrullasPresentes(); }
    @Override public int getPatrullasDepMineral() throws RemoteException { return depositos.get(1).getPatrullasPresentes(); }
    @Override public int getPatrullasDepPlasma()  throws RemoteException { return depositos.get(2).getPatrullasPresentes(); }

    // zona de recuperacion
    @Override public int getDelegadosEnRecuperacion() throws RemoteException { return sistemaCentral.getDelegadosEnRecuperacion(); }
    @Override public int getPatrullasEnRecuperacion() throws RemoteException { return sistemaCentral.getPatrullasEnRecuperacion(); }

    // base de saqueadores
    @Override public int getSaqueadoresEnBase() throws RemoteException { return baseSaqueadores.getSaqueadoresEnBase(); }

    // ==========================================
    // COMANDOS DE ESCRITURA DESDE LA RED
    // ==========================================
    // A diferencia de los métodos anteriores que solo leen datos, estos comandos alteran el servidor.
    // Acceden al monitor del depósito y ejecutan su método vaciar, el cual está protegido por 'synchronized'
    // para notificar (notifyAll) correctamente a los delegados que estén haciendo cola localmente en el servidor.
    // operaciones de vaciado
    @Override public void vaciarDepositoCristal() throws RemoteException { depositos.get(0).vaciarDeposito(); }
    @Override public void vaciarDepositoMineral() throws RemoteException { depositos.get(1).vaciarDeposito(); }
    @Override public void vaciarDepositoPlasma()  throws RemoteException { depositos.get(2).vaciarDeposito(); }
}