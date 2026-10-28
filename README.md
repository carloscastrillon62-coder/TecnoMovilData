# TecnoMovil Data — Procesamiento funcional de datos de transporte

Módulo en **Java puro (sin frameworks)** que procesa registros de validadores del
sistema de transporte urbano de TecnoValle con **programación funcional**:
funciones puras, colecciones inmutables, lambdas, funciones de orden superior
y pipelines de **Streams** (secuenciales o con `parallelStream()`).

Caso de estudio: Unidad 2 — Programación Orientada a Objetos II.

## Requisitos

- JDK 17 o superior (usa `record`).

## Estructura

```
src/tecnomovil/
├── Main.java                         Ejecuta el caso completo y muestra los resultados
├── modelo/
│   ├── Accion.java                   ENTRADA / SALIDA
│   ├── Registro.java                 Registro inmutable (idUsuario, ruta, estacion, accion, timestamp)
│   ├── Tramo.java                    Desplazamiento entre dos estaciones
│   └── ReporteRuta.java              Resultado de ocupación de una ruta
├── datos/
│   └── GeneradorDatos.java           Genera ~1 millón de registros simulados (determinista)
├── procesamiento/
│   ├── AnalizadorTransporte.java     Funciones puras: tareas a) a f)
│   └── ResultadoAnalisis.java        Agrupa los resultados de todas las tareas
└── pruebas/
    └── PruebasBasicas.java           Pruebas sin frameworks con datos conocidos
```

## Tareas implementadas

| Tarea | Método en `AnalizadorTransporte` |
|---|---|
| a) Afluencia por estación | `afluenciaPorEstacion` |
| b) Horas pico | `flujoPorHora`, `horasPico` |
| c) Rutas más utilizadas | `usoPorRuta`, `rutasMasUtilizadas` |
| d) Patrones de viaje por usuario | `patronesDeViaje` |
| e) Tiempo promedio entre estaciones | `tiempoPromedioGeneral`, `estadisticasPorTramo` |
| f) Detección de sobrecarga | `detectarSobrecarga`, `rutasCriticas` |

## Compilar y ejecutar

**Windows (PowerShell), desde la carpeta del proyecto:**

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
java -cp out tecnomovil.Main
java -cp out tecnomovil.pruebas.PruebasBasicas
```

**Linux / macOS / Git Bash:**

```bash
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out tecnomovil.Main
java -cp out tecnomovil.pruebas.PruebasBasicas
```

Para cambiar el volumen de datos, pase la cantidad de usuarios como argumento
(cada usuario genera en promedio unos 4 registros):

```bash
java -cp out tecnomovil.Main 500000
```

En **IntelliJ IDEA** o **VS Code**: abra la carpeta, marque `src` como carpeta de
código fuente y ejecute `Main.java`.
