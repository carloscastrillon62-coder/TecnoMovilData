package tecnomovil.modelo;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Registro inmutable generado por los validadores del sistema de transporte.
 *
 * Al ser un record de Java, todos sus campos son finales: una vez creado,
 * un registro no puede modificarse (inmutabilidad).
 */
public record Registro(String idUsuario,
                       String ruta,
                       String estacion,
                       Accion accion,
                       LocalDateTime timestamp) {

    public Registro {
        Objects.requireNonNull(idUsuario, "idUsuario");
        Objects.requireNonNull(ruta, "ruta");
        Objects.requireNonNull(estacion, "estacion");
        Objects.requireNonNull(accion, "accion");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    /** Hora del día (0-23) en que ocurrió el registro. Función pura. */
    public int hora() {
        return timestamp.getHour();
    }

    /** true si el registro corresponde a una entrada. Función pura. */
    public boolean esEntrada() {
        return accion == Accion.ENTRADA;
    }

    /** true si el registro corresponde a una salida. Función pura. */
    public boolean esSalida() {
        return accion == Accion.SALIDA;
    }

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String toString() {
        return String.format("%s | %-27s | %-20s | %-7s | %s",
                idUsuario, ruta, estacion, accion.texto(), FORMATO.format(timestamp));
    }
}
