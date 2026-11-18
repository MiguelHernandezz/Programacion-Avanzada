<div align="center">

# 🧵 Programación Avanzada · UAH

**Advanced Programming (Programación Avanzada, 780014) · B.S. Computer Engineering · Universidad de Alcalá · 2025–2026**

![Java](https://img.shields.io/badge/Java_17+-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Concurrency](https://img.shields.io/badge/concurrency-threads_·_monitors_·_locks-161B22?style=flat-square)
![RMI](https://img.shields.io/badge/Java_RMI-distributed-ED8B00?style=flat-square)
![Team](https://img.shields.io/badge/team-2_people-00C853?style=flat-square)

</div>

Two **concurrent and distributed** simulations in Java. Each one runs dozens of threads that share resources through monitors and locks, shows the live state in a Swing GUI, and exposes it to a **remote client over Java RMI**.

> Done in a **team of 2**; my teammate's name is omitted for privacy.

---

## 📁 Contents

| Folder | Project | Concurrency tools |
|---|---|---|
| [`PECL2-batalla-hawkins`](PECL2-batalla-hawkins) | **Battle of Hawkins**: kids and Demogorgons moving between Hawkins and the Upside Down through portals | `ReentrantLock` (fair) + 3 `Condition`s, `synchronized` monitors, `AtomicInteger`, RMI |
| [`PECL-junio-federacion-galactica`](PECL-junio-federacion-galactica) | **Galactic Federation** (June session): delegates mining 5 planets, patrols and raiders competing for resource depots | `synchronized` monitors per planet/depot, `ConcurrentHashMap`, RMI |

---

## PECL2 · Battle of Hawkins

- **Threads:** every kid (`Nino`) and every Demogorgon is a thread; a `ControladorEventos` thread fires random global events.
- **Portals as monitors:** each `Portal` uses a fair `ReentrantLock` with three `Condition`s. Kids cross in groups, wait for a free portal, and returning kids get priority.
- **Zones** in the Upside Down (Forest, Lab, Mall, Sewers) are shared state guarded by the central `GestorMundo`.
- **Pause / resume:** the whole world can be paused. A separate monitor pauses only the Demogorgons when Eleven steps in.
- **Distributed:** `GestorMundo` is a `UnicastRemoteObject`. The remote client shows kids per zone, the Demogorgon ranking, portal traffic and the current event, and can pause or resume the server.
- Every event is appended to a timestamped log, `hawkins.txt`.

## PECL (June) · Galactic Federation

- **Threads:** delegates, patrols and raiders (`Delegado`, `Patrulla`, `Saqueador`), plus a reinforcements orchestrator and an actor generator.
- **Monitors:** each of the 5 mining planets and each resource depot (crystal, mineral, plasma) has its own lock, so threads on different planets never block each other. Depots have a maximum capacity, and a raider attack puts a depot on alert, which lets the generator send in reinforcements.
- **Thread-safe log:** a shared `GestorLog` monitor writes `federacion_galactica.txt`.
- **Distributed:** the RMI interface exposes stock per depot, delegates and patrols per planet and depot, units recovering and raiders at base. The remote client can also **empty a depot**.

---

## ▶️ Run it

Requires **Java 17+** and **Maven**. Each project has a **server** (simulation + GUI + RMI registry on port 1099) and a **remote client**: start the server first.

```bash
# Battle of Hawkins
cd PECL2-batalla-hawkins
mvn package
java -jar target/hawkins.jar                     # server
java -cp target/hawkins.jar ClienteRemoto         # remote client (second terminal)

# Galactic Federation
cd PECL-junio-federacion-galactica
mvn package
java -jar target/federacion.jar                                         # server
java -cp target/federacion.jar poo.pecl_junio_progra.Interfaz_Cliente   # remote client
```

The `.txt` file in each folder is a sample log from a full run. Running the server overwrites it.

---

<div align="center">

Miguel Hernández López · [GitHub](https://github.com/MiguelHernandezz) · [LinkedIn](https://www.linkedin.com/in/miguel-hernandez-lopez/)

</div>
