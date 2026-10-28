package tecnomovil.modelo;

/**
 * Desplazamiento de un usuario entre dos estaciones consecutivas
 * (una entrada seguida de su salida). Inmutable.
 */
public record Tramo(String origen, String destino, double minutos) {

    /** Nombre legible del tramo, por ejemplo "Universidad -> Estadio". */
    public String nombre() {
        return origen + " -> " + destino;
    }
}
