package tecnomovil.datos;

import tecnomovil.modelo.Accion;
import tecnomovil.modelo.Registro;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Genera un conjunto grande de registros simulados del sistema de transporte.
 *
 * La generación es DETERMINISTA: en lugar de usar java.util.Random (que guarda
 * un estado interno que cambia en cada llamada), cada valor "aleatorio" se
 * calcula con una función hash a partir de (semilla, usuario, viaje, sal).
 * Así, la misma entrada produce siempre la misma salida: son funciones puras,
 * y por eso la generación también puede hacerse en paralelo sin riesgos.
 */
public final class GeneradorDatos {

    public static final LocalDate FECHA = LocalDate.of(2026, 10, 26);

    public static final List<String> RUTAS = List.of(
            "R01 Troncal Norte", "R02 Troncal Sur", "R03 Avenida Central",
            "R04 Circular Oriental", "R05 Alimentadora Las Palmas",
            "R06 Alimentadora El Bosque", "R07 Expreso Universidad", "R08 Nocturna");

    public static final List<String> ESTACIONES = List.of(
            "Terminal Norte", "Plaza Central", "Universidad", "Hospital General",
            "Parque Industrial", "Centro Historico", "Estadio", "Mercado Mayor",
            "Terminal Sur", "Barrio La Esperanza", "Zona Franca", "Aeropuerto",
            "Biblioteca Publica", "San Antonio", "Los Pinos");

    /** Peso relativo de cada ruta: unas rutas se usan más que otras. */
    private static final List<Integer> PESOS_RUTA = List.of(22, 18, 16, 12, 10, 9, 8, 5);

    /** Peso de la hora (0-23) en que un usuario hace su primer viaje del día. */
    private static final List<Integer> PESOS_PRIMER_VIAJE = List.of(
            0, 0, 0, 0, 1, 5, 14, 17, 10, 5, 3, 3, 3, 2, 2, 1, 1, 0, 0, 0, 0, 0, 0, 0);

    /** Peso de la hora (0-23) en que un usuario inicia su viaje de regreso. */
    private static final List<Integer> PESOS_REGRESO = List.of(
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 3, 3, 4, 7, 12, 14, 9, 5, 3, 1, 0);

    /** Peso de cada estación como destino: centros de estudio, trabajo y comercio atraen más viajes. */
    private static final List<Integer> PESOS_DESTINO = List.of(
            6, 12, 11, 7, 9, 10, 4, 7, 6, 3, 5, 3, 4, 3, 3);

    /** Peso de la cantidad de viajes diarios por usuario: 1, 2, 3 o 4 viajes. */
    private static final List<Integer> PESOS_CANTIDAD_VIAJES = List.of(15, 60, 15, 10);

    /** Viaje interno de un usuario. Inmutable. */
    private record Viaje(int numero, int casa, String ruta, int origen, int destino,
                         LocalDateTime inicio, LocalDateTime fin) {
    }

    private GeneradorDatos() {
        // Clase de utilidades: no se instancia.
    }

    /**
     * Genera los registros de un día completo.
     *
     * @param usuarios cantidad de usuarios simulados (cada uno hace de 1 a 4 viajes)
     * @param semilla  semilla para reproducir exactamente los mismos datos
     * @return lista INMUTABLE de registros ordenada por fecha y hora
     */
    public static List<Registro> generar(int usuarios, long semilla) {
        return IntStream.range(0, usuarios)
                .parallel()
                .boxed()
                .flatMap(u -> jornadaDeUsuario(u, semilla))
                .sorted(Comparator.comparing(Registro::timestamp)
                        .thenComparing(Registro::idUsuario)
                        .thenComparing(Registro::accion))
                .collect(Collectors.toUnmodifiableList());
    }

    /** Registros (entrada y salida) de todos los viajes de un usuario en el día. */
    private static Stream<Registro> jornadaDeUsuario(int usuario, long semilla) {
        String id = String.format("U%06d", usuario);
        int cantidad = elegirPonderado(PESOS_CANTIDAD_VIAJES, uniforme(semilla, usuario, 0, 1)) + 1;

        return Stream.iterate(primerViaje(usuario, semilla), v -> siguienteViaje(v, usuario, semilla))
                .limit(cantidad)
                .takeWhile(v -> v.fin().toLocalDate().equals(FECHA))
                .flatMap(v -> Stream.of(
                        new Registro(id, v.ruta(), ESTACIONES.get(v.origen()), Accion.ENTRADA, v.inicio()),
                        new Registro(id, v.ruta(), ESTACIONES.get(v.destino()), Accion.SALIDA, v.fin())));
    }

    private static Viaje primerViaje(int usuario, long semilla) {
        int casa = entero(semilla, usuario, 0, 10, ESTACIONES.size());
        int destino = destinoPonderado(casa, uniforme(semilla, usuario, 0, 11));
        int hora = elegirPonderado(PESOS_PRIMER_VIAJE, uniforme(semilla, usuario, 0, 12));
        LocalDateTime inicio = FECHA.atTime(hora,
                entero(semilla, usuario, 0, 13, 60),
                entero(semilla, usuario, 0, 14, 60));
        return crearViaje(0, casa, casa, destino, inicio, usuario, semilla);
    }

    /** El siguiente viaje parte de la estación donde terminó el anterior. */
    private static Viaje siguienteViaje(Viaje anterior, int usuario, long semilla) {
        int k = anterior.numero() + 1;
        int origen = anterior.destino();
        boolean regresaACasa = uniforme(semilla, usuario, k, 20) < 0.7 && origen != anterior.casa();
        int destino = regresaACasa
                ? anterior.casa()
                : destinoPonderado(origen, uniforme(semilla, usuario, k, 21));
        LocalDateTime minimo = anterior.fin().plusMinutes(30);
        LocalDateTime inicio = k == 1
                ? mayor(minimo, FECHA.atTime(
                        elegirPonderado(PESOS_REGRESO, uniforme(semilla, usuario, k, 22)),
                        entero(semilla, usuario, k, 23, 60),
                        entero(semilla, usuario, k, 24, 60)))
                : minimo.plusMinutes(entero(semilla, usuario, k, 25, 240));
        return crearViaje(k, anterior.casa(), origen, destino, inicio, usuario, semilla);
    }

    private static Viaje crearViaje(int numero, int casa, int origen, int destino,
                                    LocalDateTime inicio, int usuario, long semilla) {
        String ruta = RUTAS.get(elegirPonderado(PESOS_RUTA, uniforme(semilla, usuario, numero, 30)));
        long duracion = 4L + 3L * Math.abs(origen - destino) + entero(semilla, usuario, numero, 31, 7);
        return new Viaje(numero, casa, ruta, origen, destino, inicio, inicio.plusMinutes(duracion));
    }

    // ---------------------------------------------------------------------
    // Funciones puras de apoyo
    // ---------------------------------------------------------------------

    /** Elige un destino según su popularidad; si coincide con el origen, toma la siguiente estación. */
    private static int destinoPonderado(int origen, double u) {
        int elegido = elegirPonderado(PESOS_DESTINO, u);
        return elegido == origen ? (elegido + 1) % ESTACIONES.size() : elegido;
    }

    /** La mayor de dos fechas. */
    private static LocalDateTime mayor(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    /** Elige un índice según los pesos dados y un número u en [0, 1). */
    static int elegirPonderado(List<Integer> pesos, double u) {
        int total = pesos.stream().mapToInt(Integer::intValue).sum();
        double objetivo = u * total;
        return IntStream.range(0, pesos.size())
                .filter(i -> pesos.subList(0, i + 1).stream().mapToInt(Integer::intValue).sum() > objetivo)
                .findFirst()
                .orElse(pesos.size() - 1);
    }

    /** Número pseudoaleatorio en [0, 1) que depende SOLO de sus argumentos. */
    static double uniforme(long semilla, int usuario, int viaje, int sal) {
        long x = mezclar(semilla ^ mezclar(usuario * 0x9E3779B97F4A7C15L
                + viaje * 0xC2B2AE3D27D4EB4FL + sal));
        return (x >>> 11) * 0x1.0p-53;
    }

    /** Entero pseudoaleatorio en [0, limite). */
    static int entero(long semilla, int usuario, int viaje, int sal, int limite) {
        return (int) (uniforme(semilla, usuario, viaje, sal) * limite);
    }

    /** Función de mezcla SplitMix64: transforma un número en otro "desordenado". */
    private static long mezclar(long z) {
        final long a = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        final long b = (a ^ (a >>> 27)) * 0x94D049BB133111EBL;
        return b ^ (b >>> 31);
    }
}
