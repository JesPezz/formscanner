# INCIDENTES

Bitácora de incidencias del proyecto FormScanner. Formato por entrada:
`fecha · app · síntoma · causa raíz · solución · lección`.

---

## 2026-09-20 · Núcleo OMR (formscanner-api) · Las imágenes de muestra `samples/test/` devuelven todas las preguntas vacías · Desalineamiento del marco de esquinas

- **Síntoma:** ejecutando el pipeline completo (CLI y API) sobre la muestra de
  examen tipo test (`T00001.jpg` + `test_template.xtmpl`), las 40 preguntas de
  opción múltiple se detectan como sin respuesta. Solo funcionan los códigos
  de barras (`i.course`, `i.university`, parcialmente `id1`-`id6`).
- **Causa raíz:** las esquinas detectadas por `CornerDetector` en las imágenes
  `test/` no coinciden con las almacenadas en la plantilla. Detectadas
  `TL=[189,96] BR=[2333,3304]` frente a las de la plantilla
  `TL=[203,163] BR=[2313,3237]` (~67 px de desvío vertical) y con diagonal
  `1.4924e7` frente a `1.3930e7` (escala ≈ `sqrt(1.071) ≈ 1.035`). La
  transformación `calcResponsePoint` (en `FormScannerDetector`, usada por
  `FieldDetector`) traslada/rota/escala los puntos de la plantilla según ese
  marco de esquinas, emplazándolos fuera de las burbujas reales. El relleno
  medido por `isFilled` queda en el 3-7 % (por debajo del umbral de densidad de
  la plantilla, 40 %), por lo que ninguna opción supera el umbral.
- **Comprobación:** las burbujas no se detectan por coordenadas erróneas en la
  plantilla: muestreando las coordenadas brutas sin transformar, la opción C de
  `Question31` está marcada (`ratio ≈ 0.99` en `[2048,1903]`). El defecto está
  en el alineamiento del marco de esquinas, no en los puntos definidos.
- **Solución:** pendiente. Se abordará en el paso 2/4 del roadmap
  (generación automática de plantillas y mejora de precisión). Queda registrado
  como test de regresión `@Disabled` (`OMRSampleTest.muestraTestRegresionBurbujas`):
  cuando se corrija, `Question31` debe detectar `C`.
- **Lección:** la precisión del reconocimiento depende críticamente del
  alineamiento entre las esquinas detectadas y las de la plantilla; un desvío
  pequeño ya rompe los resultados. El asistente de desarrollo no puede inspeccionar
  visualmente las imágenes: toda verificación debe hacerse programáticamente.