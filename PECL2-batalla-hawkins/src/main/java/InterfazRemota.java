// Importa la interfaz base de Java RMI. Cualquier clase que sus instancias vayan a ser accesibles por red debe heredar de ella
import java.rmi.Remote;
// Importa la excepción obligatoria en RMI. Sirve para manejar errores de comunicación (caídas de red, servidor apagado, etc.)
import java.rmi.RemoteException;

// Interfaz que define el "contrato" de comunicación entre el servidor (GestorMundo) y el cliente remoto (ClienteRemoto)
public interface InterfazRemota extends Remote {
    
    // Método para obtener la suma total de niños que están en el mundo normal (zonas seguras)
    // Todos los métodos deben lanzar 'throws RemoteException' obligatoriamente en RMI
    int getTotalNinosHawkins() throws RemoteException;
    
    // Método que solicita al servidor el texto con el Top 3 de Demogorgons ordenados por capturas
    String getRankingDemos() throws RemoteException;
    
    // Método que pide al servidor la información del evento global actual (tipo y tiempo restante)
    String getEstadoEvento() throws RemoteException;
    
    // Métodos para obtener información estructurada en formato HTML, lista para ser insertada en los JLabel de la interfaz gráfica remota
    String getPortalesHTML() throws RemoteException; // Información de tráfico de los portales
    String getUbicacionesNinosHTML() throws RemoteException; // Cantidad de niños por zona en el Upside Down
    String getUbicacionesDemosHTML() throws RemoteException; // Cantidad de Demogorgons por zona en el Upside Down
    
    // Método para enviarle al servidor la orden de pausar o reanudar la simulación
    void conmutarPausa() throws RemoteException;
    
    // Método para preguntarle al servidor si la simulación está actualmente pausada o en curso
    boolean isPausado() throws RemoteException;
}