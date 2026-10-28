package tecnomovil.modelo;

/**
 * Resultado inmutable del análisis de ocupación de una ruta.
 *
 * @param ruta                 nombre de la ruta
 * @param horaPico             hora con más entradas en la ruta
 * @param pasajerosHoraPico    entradas registradas en esa hora
 * @param porcentajeOcupacion  pasajerosHoraPico / capacidad por hora * 100
 * @param critica              true si la ocupación alcanza o supera el umbral
 */
public record ReporteRuta(String ruta,
                          int horaPico,
                          long pasajerosHoraPico,
                          double porcentajeOcupacion,
                          boolean critica) {
}
