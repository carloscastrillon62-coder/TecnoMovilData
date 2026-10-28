package tecnomovil;

import tecnomovil.datos.GeneradorDatos;
import tecnomovil.modelo.Accion;
import tecnomovil.modelo.Registro;
import tecnomovil.modelo.ReporteRuta;
import tecnomovil.procesamiento.AnalizadorTransporte;
import tecnomovil.procesamiento.ResultadoAnalisis;

import java.time.LocalDateTime;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Punto de entrada de TecnoMovil Data.
 *
 * Esta clase es la ÚNICA que produce efectos secundarios (imprimir en consola
 * y medir tiempos). Toda la lógica de procesamiento vive en funciones puras
 * de AnalizadorTransporte. Así se separa el "qué se calcula" del "cómo se muestra".
 *
 * Uso: java -cp out tecnomovil.Main [usuarios]   (por defecto 250000 usuarios)
 */
public class Main {

    private static final Locale CO = Locale.forLanguageTag("es-CO");
    private static final long SEMILLA = 2026L;
    private static final long CAPACIDAD_POR_HORA = 11_000L;   // pasajeros por ruta por hora (simulado)
    private static final double UMBRAL_CRITICO = 85.0;        // % de ocupacion
    private static final boolean SECUENCIAL = false;

    public static void main(String[] args) {
        int usuarios = args.length > 0 ? Integer.parseInt(args[0]) : 250_000;

        titulo("TECNOMOVIL DATA - Procesamiento funcional de datos de transporte urbano");
        System.out.printf(CO, "Generando datos simulados de %,d usuarios para el %s...%n", usuarios, GeneradorDatos.FECHA);

        Medicion<List<Registro>> generacion = medir(() -> GeneradorDatos.generar(usuarios, SEMILLA));
        List<Registro> registros = generacion.valor();
        System.out.printf(CO, "Registros generados: %,d (en %,.2f s)%n", registros.size(), generacion.segundos());
        System.out.println("Primeros registros:");
        registros.stream().limit(5).forEach(r -> System.out.println("  " + r));

        int huellaInicial = registros.hashCode();

        // a) Afluencia por estacion
        titulo("a) Afluencia por estacion (entradas del dia)");
        AnalizadorTransporte.topN(AnalizadorTransporte.afluenciaPorEstacion(registros, SECUENCIAL), 15)
                .forEach(e -> System.out.printf(CO, "  %-22s %,10d%n", e.getKey(), e.getValue()));

        // b) Horas pico
        titulo("b) Flujo por hora del dia (entradas + salidas)");
        Map<Integer, Long> porHora = AnalizadorTransporte.flujoPorHora(registros, SECUENCIAL);
        long maximo = porHora.values().stream().mapToLong(Long::longValue).max().orElse(1);
        porHora.forEach((hora, total) -> System.out.printf(CO, "  %02d:00  %,9d  %s%n",
                hora, total, "#".repeat((int) (total * 50 / maximo))));
        System.out.println("  Horas pico:");
        AnalizadorTransporte.horasPico(registros, 3, SECUENCIAL)
                .forEach(e -> System.out.printf(CO, "    %02d:00 - %02d:59  ->  %,d registros%n",
                        e.getKey(), e.getKey(), e.getValue()));

        // c) Rutas mas utilizadas
        titulo("c) Rutas mas utilizadas (viajes iniciados)");
        AnalizadorTransporte.rutasMasUtilizadas(registros, 8, SECUENCIAL)
                .forEach(e -> System.out.printf(CO, "  %-28s %,10d%n", e.getKey(), e.getValue()));

        // d) Patrones de viaje
        titulo("d) Patrones de viaje por usuario (muestra de 5 usuarios)");
        Map<String, List<String>> patrones = AnalizadorTransporte.patronesDeViaje(registros, SECUENCIAL);
        System.out.printf(CO, "  Usuarios con viajes registrados: %,d%n", patrones.size());
        patrones.entrySet().stream().limit(5)
                .forEach(e -> System.out.printf("  %s: %s%n", e.getKey(), String.join(" -> ", e.getValue())));

        // e) Tiempo promedio entre estaciones
        titulo("e) Tiempo promedio entre estaciones consecutivas");
        System.out.printf(CO, "  Promedio general: %,.2f minutos%n",
                AnalizadorTransporte.tiempoPromedioGeneral(registros, SECUENCIAL));
        System.out.println("  Los 5 tramos mas frecuentes:");
        AnalizadorTransporte.estadisticasPorTramo(registros, SECUENCIAL).entrySet().stream()
                .sorted(Map.Entry.<String, DoubleSummaryStatistics>comparingByValue(
                        (x, y) -> Long.compare(y.getCount(), x.getCount())))
                .limit(5)
                .forEach(e -> System.out.printf(CO, "    %-42s %,6d viajes   promedio %,6.2f min%n",
                        e.getKey(), e.getValue().getCount(), e.getValue().getAverage()));

        // f) Deteccion de sobrecarga
        titulo(String.format(CO, "f) Deteccion de sobrecarga (capacidad %,d pasajeros/hora, umbral %.0f%%)",
                CAPACIDAD_POR_HORA, UMBRAL_CRITICO));
        List<ReporteRuta> reportes = AnalizadorTransporte.detectarSobrecarga(
                registros, CAPACIDAD_POR_HORA, UMBRAL_CRITICO, SECUENCIAL);
        System.out.printf("  %-28s %-9s %10s %10s   %s%n", "Ruta", "Hora pico", "Pasajeros", "Ocupacion", "Estado");
        reportes.forEach(r -> System.out.printf(CO, "  %-28s %02d:00     %,10d %9.1f%%   %s%n",
                r.ruta(), r.horaPico(), r.pasajerosHoraPico(), r.porcentajeOcupacion(),
                r.critica() ? "CRITICA" : "normal"));
        System.out.println("  Rutas criticas: " + AnalizadorTransporte.rutasCriticas(reportes));

        // Inmutabilidad
        titulo("Verificacion de inmutabilidad");
        try {
            registros.add(new Registro("U999999", "R99", "Prueba", Accion.ENTRADA, LocalDateTime.now()));
            System.out.println("  ERROR: la lista se pudo modificar");
        } catch (UnsupportedOperationException ex) {
            System.out.println("  Intento de agregar un registro a la lista original -> UnsupportedOperationException");
        }
        System.out.println("  La lista original no cambio tras todos los analisis: "
                + (registros.hashCode() == huellaInicial));

        // Paralelismo
        titulo("Comparacion secuencial vs. paralelo (parallelStream)");
        Medicion<ResultadoAnalisis> secuencial = medir(() ->
                ResultadoAnalisis.calcular(registros, CAPACIDAD_POR_HORA, UMBRAL_CRITICO, false));
        Medicion<ResultadoAnalisis> paralelo = medir(() ->
                ResultadoAnalisis.calcular(registros, CAPACIDAD_POR_HORA, UMBRAL_CRITICO, true));
        System.out.printf(CO, "  Nucleos disponibles: %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf(CO, "  Secuencial: %,.2f s   |   Paralelo: %,.2f s%n", secuencial.segundos(), paralelo.segundos());
        System.out.println("  Ambos modos producen los mismos resultados: "
                + secuencial.valor().equivalenteA(paralelo.valor()));
        System.out.println();
    }

    // ---------------- Utilidades de presentacion (efectos secundarios) ----------------

    private record Medicion<T>(T valor, double segundos) {
    }

    private static <T> Medicion<T> medir(Supplier<T> calculo) {
        long inicio = System.nanoTime();
        T valor = calculo.get();
        return new Medicion<>(valor, (System.nanoTime() - inicio) / 1e9);
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("=".repeat(Math.max(70, texto.length())));
        System.out.println(texto);
        System.out.println("=".repeat(Math.max(70, texto.length())));
    }
}
