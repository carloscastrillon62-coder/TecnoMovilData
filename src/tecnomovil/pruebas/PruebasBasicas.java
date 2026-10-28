package tecnomovil.pruebas;

import tecnomovil.modelo.Accion;
import tecnomovil.modelo.Registro;
import tecnomovil.modelo.ReporteRuta;
import tecnomovil.procesamiento.AnalizadorTransporte;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Pruebas sencillas, sin frameworks externos, con un conjunto pequeño de
 * datos cuyo resultado se conoce de antemano.
 *
 * Uso: java -cp out tecnomovil.pruebas.PruebasBasicas
 */
public class PruebasBasicas {

    private static final LocalDateTime DIA = LocalDateTime.of(2026, 10, 26, 0, 0);

    private static Registro r(String usuario, String ruta, String estacion, Accion accion, int hora, int minuto) {
        return new Registro(usuario, ruta, estacion, accion, DIA.withHour(hora).withMinute(minuto));
    }

    /** Datos de prueba: 2 usuarios, 3 viajes. */
    private static final List<Registro> DATOS = List.of(
            r("U1", "R01", "Norte", Accion.ENTRADA, 7, 0),
            r("U1", "R01", "Centro", Accion.SALIDA, 7, 20),
            r("U2", "R02", "Norte", Accion.ENTRADA, 7, 10),
            r("U2", "R02", "Sur", Accion.SALIDA, 7, 40),
            r("U1", "R01", "Centro", Accion.ENTRADA, 18, 0),
            r("U1", "R01", "Norte", Accion.SALIDA, 18, 30));

    public static void main(String[] args) {
        List<Boolean> resultados = List.of(
                prueba("a) afluencia por estacion",
                        () -> AnalizadorTransporte.afluenciaPorEstacion(DATOS, false)
                                .equals(Map.of("Norte", 2L, "Centro", 1L))),
                prueba("b) hora pico es las 7",
                        () -> AnalizadorTransporte.horasPico(DATOS, 1, false).get(0).getKey() == 7),
                prueba("c) ruta mas usada es R01",
                        () -> AnalizadorTransporte.rutasMasUtilizadas(DATOS, 1, false).get(0).getKey().equals("R01")),
                prueba("d) patron de U1 es Norte -> Centro -> Norte",
                        () -> AnalizadorTransporte.patronesDeViaje(DATOS, false).get("U1")
                                .equals(List.of("Norte", "Centro", "Norte"))),
                prueba("e) tiempo promedio = (20 + 30 + 30) / 3 minutos",
                        () -> Math.abs(AnalizadorTransporte.tiempoPromedioGeneral(DATOS, false) - 80.0 / 3) < 1e-9),
                prueba("f) R01 critica con capacidad 1 y umbral 100%",
                        () -> AnalizadorTransporte.detectarSobrecarga(DATOS, 1, 100.0, false).stream()
                                .filter(rep -> rep.ruta().equals("R01"))
                                .allMatch(ReporteRuta::critica)),
                prueba("funcion pura: misma entrada -> mismo resultado",
                        () -> AnalizadorTransporte.patronesDeViaje(DATOS, false)
                                .equals(AnalizadorTransporte.patronesDeViaje(DATOS, true))),
                prueba("inmutabilidad: el resultado no se puede modificar",
                        () -> lanzaExcepcion(() -> AnalizadorTransporte.afluenciaPorEstacion(DATOS, false).put("X", 1L))));

        long aprobadas = resultados.stream().filter(Boolean::booleanValue).count();
        System.out.printf("%nPruebas aprobadas: %d de %d%n", aprobadas, resultados.size());
    }

    private static boolean prueba(String nombre, Supplier<Boolean> condicion) {
        boolean ok = condicion.get();
        System.out.printf("[%s] %s%n", ok ? "OK" : "FALLA", nombre);
        return ok;
    }

    private static boolean lanzaExcepcion(Runnable accion) {
        try {
            accion.run();
            return false;
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }
}
