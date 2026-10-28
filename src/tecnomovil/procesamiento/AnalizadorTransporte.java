package tecnomovil.procesamiento;

import tecnomovil.modelo.Registro;
import tecnomovil.modelo.ReporteRuta;
import tecnomovil.modelo.Tramo;

import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Módulo funcional de procesamiento y agregación de datos de TecnoMovil Data.
 *
 * Reglas que cumplen TODOS los métodos de esta clase:
 *  - Son funciones puras: su resultado depende solo de sus parámetros y no
 *    producen efectos secundarios (no imprimen, no leen archivos, no modifican
 *    variables externas).
 *  - Nunca modifican la lista de entrada (inmutabilidad) y devuelven
 *    colecciones inmutables.
 *  - Usan lambdas, referencias a métodos y pipelines de Streams (estilo declarativo).
 *  - Aceptan el parámetro 'paralelo' para procesar con parallelStream().
 */
public final class AnalizadorTransporte {

    /** Ordena los registros por fecha y hora. */
    public static final Comparator<Registro> POR_TIEMPO = Comparator.comparing(Registro::timestamp);

    private AnalizadorTransporte() {
        // Clase de funciones puras: no se instancia.
    }

    // =====================================================================
    // Funciones de orden superior reutilizables
    // =====================================================================

    /** Crea el stream secuencial o paralelo según se indique. */
    private static Stream<Registro> flujo(List<Registro> registros, boolean paralelo) {
        return paralelo ? registros.parallelStream() : registros.stream();
    }

    /**
     * FUNCIÓN DE ORDEN SUPERIOR: recibe otras funciones como parámetros.
     * Cuenta los registros que cumplen 'filtro', agrupados por la 'clave' indicada.
     *
     * @param filtro predicado que decide qué registros se cuentan
     * @param clave  función que extrae la clave de agrupación de cada registro
     * @return mapa inmutable y ordenado clave -> cantidad
     */
    public static <K extends Comparable<K>> Map<K, Long> contarPor(List<Registro> registros,
                                                                   Predicate<Registro> filtro,
                                                                   Function<Registro, K> clave,
                                                                   boolean paralelo) {
        return flujo(registros, paralelo)
                .filter(filtro)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(clave, TreeMap::new, Collectors.counting()),
                        Collections::unmodifiableMap));
    }

    /** Devuelve los 'n' elementos con mayor conteo, de mayor a menor. */
    public static <K> List<Map.Entry<K, Long>> topN(Map<K, Long> conteos, int n) {
        return conteos.entrySet().stream()
                .sorted(Map.Entry.<K, Long>comparingByValue().reversed())
                .limit(n)
                .map(e -> Map.entry(e.getKey(), e.getValue()))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * FUNCIÓN DE ORDEN SUPERIOR que DEVUELVE una función: crea una función que
     * convierte una cantidad de pasajeros en porcentaje de la capacidad dada.
     */
    public static Function<Long, Double> porcentajeSobre(long capacidad) {
        return pasajeros -> pasajeros * 100.0 / capacidad;
    }

    // =====================================================================
    // a) Afluencia por estación
    // =====================================================================

    /** Cantidad de entradas registradas en cada estación durante el día. */
    public static Map<String, Long> afluenciaPorEstacion(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, Registro::esEntrada, Registro::estacion, paralelo);
    }

    // =====================================================================
    // b) Horas pico
    // =====================================================================

    /** Flujo total (entradas + salidas) por cada hora del día. */
    public static Map<Integer, Long> flujoPorHora(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, r -> true, Registro::hora, paralelo);
    }

    /** Las 'n' horas con mayor flujo de pasajeros. */
    public static List<Map.Entry<Integer, Long>> horasPico(List<Registro> registros, int n, boolean paralelo) {
        return topN(flujoPorHora(registros, paralelo), n);
    }

    // =====================================================================
    // c) Rutas más utilizadas
    // =====================================================================

    /** Cantidad de viajes (entradas) por ruta. */
    public static Map<String, Long> usoPorRuta(List<Registro> registros, boolean paralelo) {
        return contarPor(registros, Registro::esEntrada, Registro::ruta, paralelo);
    }

    /** Las 'n' rutas con mayor volumen de uso. */
    public static List<Map.Entry<String, Long>> rutasMasUtilizadas(List<Registro> registros, int n, boolean paralelo) {
        return topN(usoPorRuta(registros, paralelo), n);
    }

    // =====================================================================
    // d) Patrones de viaje por usuario
    // =====================================================================

    /** Agrupa los registros por usuario, cada grupo ordenado por fecha y hora. */
    public static Map<String, List<Registro>> registrosPorUsuario(List<Registro> registros, boolean paralelo) {
        return flujo(registros, paralelo)
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Registro::idUsuario, TreeMap::new,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        lista -> lista.stream()
                                                .sorted(POR_TIEMPO)
                                                .collect(Collectors.toUnmodifiableList()))),
                        Collections::unmodifiableMap));
    }

    /**
     * Para cada usuario, el listado de estaciones visitadas en orden.
     * Si una estación se repite seguida (salida y nueva entrada en la misma
     * estación) se muestra una sola vez.
     */
    public static Map<String, List<String>> patronesDeViaje(List<Registro> registros, boolean paralelo) {
        return registrosPorUsuario(registros, paralelo).entrySet().stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                e -> sinRepetidosConsecutivos(e.getValue().stream()
                                        .map(Registro::estacion)
                                        .collect(Collectors.toUnmodifiableList())),
                                (a, b) -> a,
                                TreeMap::new),
                        Collections::unmodifiableMap));
    }

    /** [A, B, B, C] -> [A, B, C]. No modifica la lista recibida. */
    public static List<String> sinRepetidosConsecutivos(List<String> estaciones) {
        return IntStream.range(0, estaciones.size())
                .filter(i -> i == 0 || !estaciones.get(i).equals(estaciones.get(i - 1)))
                .mapToObj(estaciones::get)
                .collect(Collectors.toUnmodifiableList());
    }

    // =====================================================================
    // e) Tiempo promedio entre estaciones
    // =====================================================================

    /** Todos los tramos (entrada seguida de salida) de todos los usuarios. */
    public static List<Tramo> tramos(List<Registro> registros, boolean paralelo) {
        Stream<List<Registro>> porUsuario = paralelo
                ? registrosPorUsuario(registros, true).values().parallelStream()
                : registrosPorUsuario(registros, false).values().stream();
        return porUsuario
                .flatMap(AnalizadorTransporte::tramosDeUsuario)
                .collect(Collectors.toUnmodifiableList());
    }

    /** Recorre los registros consecutivos de un usuario y arma sus tramos. */
    static Stream<Tramo> tramosDeUsuario(List<Registro> ordenados) {
        return IntStream.range(0, ordenados.size() - 1)
                .mapToObj(i -> Map.entry(ordenados.get(i), ordenados.get(i + 1)))
                .filter(par -> par.getKey().esEntrada()
                        && par.getValue().esSalida()
                        && !par.getKey().estacion().equals(par.getValue().estacion()))
                .map(par -> new Tramo(
                        par.getKey().estacion(),
                        par.getValue().estacion(),
                        Duration.between(par.getKey().timestamp(), par.getValue().timestamp()).toSeconds() / 60.0));
    }

    /** Tiempo promedio general, en minutos, entre estaciones consecutivas. */
    public static double tiempoPromedioGeneral(List<Registro> registros, boolean paralelo) {
        return tramos(registros, paralelo).stream()
                .mapToDouble(Tramo::minutos)
                .average()
                .orElse(0.0);
    }

    /** Estadísticas (cantidad, promedio, mínimo, máximo) de cada tramo origen -> destino. */
    public static Map<String, DoubleSummaryStatistics> estadisticasPorTramo(List<Registro> registros, boolean paralelo) {
        return tramos(registros, paralelo).stream()
                .collect(Collectors.collectingAndThen(
                        Collectors.groupingBy(Tramo::nombre, TreeMap::new,
                                Collectors.summarizingDouble(Tramo::minutos)),
                        Collections::unmodifiableMap));
    }

    // =====================================================================
    // f) Detección de sobrecarga en rutas
    // =====================================================================

    /**
     * Calcula la ocupación de cada ruta en su hora de mayor demanda y la marca
     * como CRÍTICA si alcanza o supera el umbral.
     *
     * @param capacidadPorHora pasajeros que la ruta puede atender en una hora (simulado)
     * @param umbral           porcentaje de ocupación a partir del cual la ruta es crítica
     */
    public static List<ReporteRuta> detectarSobrecarga(List<Registro> registros,
                                                       long capacidadPorHora,
                                                       double umbral,
                                                       boolean paralelo) {
        Map<String, Map<Integer, Long>> entradasPorRutaYHora = flujo(registros, paralelo)
                .filter(Registro::esEntrada)
                .collect(Collectors.groupingBy(Registro::ruta, TreeMap::new,
                        Collectors.groupingBy(Registro::hora, TreeMap::new, Collectors.counting())));

        Function<Long, Double> ocupacion = porcentajeSobre(capacidadPorHora);

        return entradasPorRutaYHora.entrySet().stream()
                .map(e -> reporteDeRuta(e.getKey(), e.getValue(), ocupacion, umbral))
                .sorted(Comparator.comparingDouble(ReporteRuta::porcentajeOcupacion).reversed())
                .collect(Collectors.toUnmodifiableList());
    }

    private static ReporteRuta reporteDeRuta(String ruta,
                                             Map<Integer, Long> entradasPorHora,
                                             Function<Long, Double> ocupacion,
                                             double umbral) {
        Map.Entry<Integer, Long> pico = entradasPorHora.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();
        double porcentaje = ocupacion.apply(pico.getValue());
        return new ReporteRuta(ruta, pico.getKey(), pico.getValue(), porcentaje, porcentaje >= umbral);
    }

    /** Solo las rutas marcadas como críticas. */
    public static List<String> rutasCriticas(List<ReporteRuta> reportes) {
        return reportes.stream()
                .filter(ReporteRuta::critica)
                .map(ReporteRuta::ruta)
                .collect(Collectors.toUnmodifiableList());
    }
}
