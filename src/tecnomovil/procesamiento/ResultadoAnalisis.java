package tecnomovil.procesamiento;

import tecnomovil.modelo.Registro;
import tecnomovil.modelo.ReporteRuta;

import java.util.List;
import java.util.Map;

/**
 * Agrupa, de forma inmutable, los resultados de las seis tareas del caso.
 */
public record ResultadoAnalisis(Map<String, Long> afluenciaPorEstacion,
                                Map<Integer, Long> flujoPorHora,
                                Map<String, Long> usoPorRuta,
                                Map<String, List<String>> patronesDeViaje,
                                double tiempoPromedioGeneral,
                                List<ReporteRuta> sobrecarga) {

    /** Ejecuta todas las tareas (a-f) sobre los mismos registros. Función pura. */
    public static ResultadoAnalisis calcular(List<Registro> registros,
                                             long capacidadPorHora,
                                             double umbral,
                                             boolean paralelo) {
        return new ResultadoAnalisis(
                AnalizadorTransporte.afluenciaPorEstacion(registros, paralelo),
                AnalizadorTransporte.flujoPorHora(registros, paralelo),
                AnalizadorTransporte.usoPorRuta(registros, paralelo),
                AnalizadorTransporte.patronesDeViaje(registros, paralelo),
                AnalizadorTransporte.tiempoPromedioGeneral(registros, paralelo),
                AnalizadorTransporte.detectarSobrecarga(registros, capacidadPorHora, umbral, paralelo));
    }

    /**
     * Compara dos resultados. El promedio se compara con tolerancia porque, en
     * paralelo, las sumas de números decimales pueden variar en el último decimal.
     */
    public boolean equivalenteA(ResultadoAnalisis otro) {
        return afluenciaPorEstacion.equals(otro.afluenciaPorEstacion)
                && flujoPorHora.equals(otro.flujoPorHora)
                && usoPorRuta.equals(otro.usoPorRuta)
                && patronesDeViaje.equals(otro.patronesDeViaje)
                && Math.abs(tiempoPromedioGeneral - otro.tiempoPromedioGeneral) < 1e-9
                && sobrecarga.equals(otro.sobrecarga);
    }
}
