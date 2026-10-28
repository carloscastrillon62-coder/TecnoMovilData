package tecnomovil.modelo;

/**
 * Tipo de validación registrada por el sistema.
 * ENTRADA: el usuario ingresa a la estación.
 * SALIDA: el usuario valida para salir o cambiar de ruta.
 */
public enum Accion {
    ENTRADA("entrada"),
    SALIDA("salida");

    private final String texto;

    Accion(String texto) {
        this.texto = texto;
    }

    public String texto() {
        return texto;
    }
}
