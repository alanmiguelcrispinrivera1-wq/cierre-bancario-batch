# Cierre bancario con Spring Batch

**Autor:** Alan Miguel Crispin Rivera

## Cómo correrlo

    docker compose up -d --wait
    ./correr.sh 2026-09-30 prueba
    ./ver-batch.sh

## Día 1 · Mi primer Job

### Boleto de salida

1. ¿Qué diferencia hay entre un proceso batch y la API REST de la Semana 3? Da dos.

El primer punto que yo destaco es el cómo se activa y cuánto procesa. La API de empleados funciona bajo demanda: está levantada esperando peticiones HTTP y responde al momento a cada una. Por ejemplo, un cliente hace un POST /api/empleados y recibe de inmediato un 201, o un 409 si el email ya existe. Cada petición trabaja con uno o pocos registros. Un proceso batch, en cambio, no espera a un usuario. Se lanza de forma programada o cuando ocurre un evento, como la llegada del archivo del cierre del día. Corre sin interacción, procesa un volumen grande de registros de una sola vez (en Steps o por chunks) y al terminar se detiene.
El segundo punto es el control del estado y los reintentos. La API REST no guarda historial de sus ejecuciones: cada petición es independiente (stateless). Si una falla, devuelve un error como 400 o 404 y es el cliente quien decide si vuelve a intentarlo. Spring Batch sí lleva un registro de cada ejecución en sus tablas de metadatos (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_STEP_EXECUTION). Por eso sabe si el cierre de una fecha terminó en COMPLETED o en FAILED. Con eso impide repetir una instancia ya completada, como el cierre del 28, y permite reintentar una que falló, como el cierre del 25.

2. ¿Qué es un Job, qué es un Step y qué es un Tasklet?

Un Job es el proceso batch completo, de principio a fin. En la práctica sería, por ejemplo, el cierre diario: todo lo que tiene que pasar para cerrar una fecha. El Job define qué pasos se ejecutan y en qué orden, y es lo que se lanza con sus parámetros (como la fecha), lo que da origen a las JobInstance y JobExecution.
Un Step es cada una de las etapas independientes en que se divide un Job. Por ejemplo, el cierre podría tener un paso para validar que llegó el archivo, otro para cargar los movimientos y otro para calcular comisiones. Cada Step se ejecuta y registra por separado (en BATCH_STEP_EXECUTION), así que si el Job falla, se puede saber en qué paso ocurrió y, al reintentar, reanudar desde ahí sin repetir los que ya terminaron bien.
Un Tasklet es una forma de implementar el trabajo de un Step: una sola tarea que se ejecuta de una vez, a través de su método execute(). Se usa para acciones puntuales que no implican procesar registros uno por uno, como borrar archivos temporales, validar que exista un archivo o ejecutar una sentencia SQL. La alternativa al Tasklet es el modelo por chunks (ItemReader → ItemProcessor → ItemWriter), que se usa cuando hay que leer, transformar y escribir grandes volúmenes de datos en bloques.
En resumen, un Job está compuesto por uno o más Steps, y cada Step hace su trabajo ya sea con un Tasklet (tarea única) o con procesamiento por chunks (lotes de registros).

3. Con tus tablas: ¿qué diferencia hay entre una **JobInstance** y una **JobExecution**?

La JobInstance identifica un trabajo lógico: el job más un conjunto específico de parámetros, como el cierre correspondiente al 2026-09-28. Es la unidad de trabajo que debe completarse. La JobExecution es cada vez que esa instancia se lanza. Por ejemplo, si el cierre falla y lo vuelvo a lanzar con los mismos parámetros, no se crea una instancia nueva: la JobInstance es la misma, pero queda registrada una segunda JobExecution asociada a ella.

4. ¿Por qué Spring Batch no deja correr dos veces el cierre del 28?

Para proteger la integridad de los datos. La instancia del 2026-09-28 ya terminó en estado COMPLETED, así que Spring Batch considera que ese trabajo ya está hecho. Si permitiera relanzarlo, operaciones como el cobro de comisiones o los movimientos de saldo podrían aplicarse por duplicado. Por eso, cualquier intento de ejecutarlo otra vez con esa misma fecha se rechaza con una JobInstanceAlreadyCompleteException.

5. (MP-4, paso 6) Si mañana llega el archivo del 25 y corres otra vez el cierre del 25, ¿será otra instancia u otra ejecución de la misma? ¿Por qué lo crees?

Sería una nueva ejecución dentro de la misma instancia. El intento anterior con fecha 2026-09-25 terminó en FAILED, por lo que esa JobInstance sigue sin completarse. Al relanzar el job con el mismo parámetro de fecha, Spring Batch no crea una instancia nueva: retoma la que ya existe y agrega un registro más en BATCH_JOB_EXECUTION que representa este nuevo intento.

## Día 2 · El primer chunk

### Boleto de salida

1. ¿Qué diferencia hay entre un step de tipo Tasklet y uno de tipo chunk?

Un Tasklet ejecuta una sola tarea completa en una única llamada y termina al devolver RepeatStatus.FINISHED. En mi proyecto, verificarArchivoStep es de este tipo: revisa que exista el archivo de la fecha y cuenta sus renglones, pero no procesa los movimientos uno por uno. Por eso en BATCH_STEP_EXECUTION aparece con READ_COUNT y WRITE_COUNT en 0 y un solo commit.
Un step de tipo chunk está pensado para procesar muchos registros. Lee los elementos uno por uno, los procesa, los junta en bloques de un tamaño fijo y escribe y confirma (commit) cada bloque por separado. cargarMovimientosStep funciona así con chunk(10): con el archivo del 2026-10-01 registró READ_COUNT 25, WRITE_COUNT 25 y COMMIT_COUNT 3. En resumen, el Tasklet sirve para acciones puntuales y el chunk para cargar o transformar volúmenes de datos.

2. ¿Qué hace cada una de las tres piezas de un chunk? ¿Cuál es opcional?

- Lector (ItemReader): obtiene los datos de la fuente, un elemento a la vez. En mi caso, movimientoReader es un FlatFileItemReader que lee el CSV de la fecha, se salta el encabezado y convierte cada renglón en un Movimiento.
- Procesador (ItemProcessor): recibe un elemento ya leído y lo transforma o valida antes de escribirlo. MovimientoProcessor limpia el tipo con trim().toUpperCase() y le quita los espacios a la cuenta. Si devolviera null, el elemento se descartaría y se sumaría a FILTER_COUNT.
- Escritor (ItemWriter): guarda el bloque completo en el destino. movimientoWriter es un JdbcBatchItemWriter que hace el INSERT en la tabla movimiento de MySQL.

El Procesador es el opcional. Sin él, lo que lee el Lector pasa directo al Escritor. El Lector y el Escritor siempre son obligatorios.

3. Con 45 movimientos y chunks de 10, ¿cuántos commits habría? ¿Y con chunks de 50?

Con chunks de 10 habría 5 commits: cuatro bloques llenos de 10 movimientos y un último bloque con los 5 restantes. Es decir, 45 / 10 redondeado hacia arriba. Mis tablas confirman esta regla: con chunk de 10, el archivo del 01 (25 movimientos) dio 3 commits y el del 02 (20 movimientos) dio 2. Con chunk de 7, el archivo del 03 (20 movimientos) dio 3 commits, en bloques de 7, 7 y 6. Con chunks de 50 habría 1 solo commit, porque los 45 movimientos caben en un bloque. La desventaja es que, si algo fallara, se haría rollback de los 45 y no solo de un bloque de 10.

4. ¿Por qué el Escritor recibe el chunk completo y no un movimiento a la vez?

Por eficiencia y por la transacción. El JdbcBatchItemWriter manda todos los INSERT del bloque a MySQL como un solo batch de JDBC, en un viaje a la base de datos, en lugar de hacer un viaje por cada movimiento. Con miles de registros, esa diferencia de rendimiento es muy grande. Además, el chunk es la unidad de transacción: los 10 movimientos se escriben y se confirman juntos en un mismo commit. Si uno falla, se hace rollback del bloque completo y la tabla no queda con un bloque guardado a medias. El Lector y el Procesador sí trabajan de uno en uno. La escritura se agrupa porque es la operación costosa y la que necesita ser atómica.

5. Mi predicción de la MP-3, paso 1: ¿qué habría pasado sin el Procesador?

El Job no habría fallado: habría terminado en COMPLETED y escrito los 20 movimientos del 2026-10-02. El problema es que se habrían guardado tal como vienen en el archivo, porque ese archivo trae el tipo escrito de varias formas: deposito, deposito (con espacio al inicio), Deposito, Retiro, RETIRO, etc. Todas caben en el VARCHAR(10) de la columna, así que MySQL no habría dado ningún error y los datos sucios habrían entrado sin aviso. La consecuencia se nota al consultar. Con el Procesador, el resumen por tipo (dia2-movimientos-2.txt) muestra solo dos grupos limpios: DEPOSITO con 25 y RETIRO con 20. Sin él, el GROUP BY tipo habría mostrado grupos extra, por ejemplo uno para deposito y otro para RETIRO, y los totales por tipo quedarían repartidos y no cuadrarían. Cualquier paso posterior que buscara exactamente 'RETIRO', como un cálculo de comisiones, se habría saltado esos movimientos. Por eso el Procesador es el lugar para normalizar los datos antes de escribirlos.

## Día 3 · Parámetros, fallas y reinicio

### Boleto de salida

1. ¿Qué diferencia hay entre una JobInstance y una JobExecution? Usa como ejemplo el cierre del 25.

La JobInstance es el trabajo lógico: el Job cierreDelDiaJob con un juego concreto de parámetros, en este caso fecha=2026-12-25. Existe una sola por fecha y representa el cierre que hay que completar. La JobExecution es cada intento de correr esa instancia.
En mis tablas, el cierre del 25 tiene una sola JobInstance (la 4) y dos JobExecution:
Ejecución 4 → FAILED. El archivo todavía no existía y verificarArchivoStep lanzó IllegalStateException: No existe el archivo del día.
Ejecución 10 → COMPLETED. Cuando llegó el archivo, volví a correr la misma fecha. cargarMovimientosStep leyó y escribió los 5 movimientos.
Fueron dos intentos, pero ambos pertenecen al mismo cierre: la instancia 4.

2. ¿En qué caso Spring Batch se niega a correr un cierre, y en qué caso lo reinicia?

Se niega cuando la JobInstance de esa fecha ya terminó en COMPLETED. Ese cierre ya está hecho, y volver a correrlo duplicaría los movimientos en la tabla. Por eso lanza JobInstanceAlreadyCompleteException, como pasó en el día 1 al repetir el cierre del 28. También rechaza el lanzamiento si esa misma instancia está corriendo en ese momento.
Lo reinicia cuando la última ejecución de esa fecha terminó en FAILED (o STOPPED). En ese caso no crea una instancia nueva: agrega una JobExecution más a la existente y continúa desde el step que falló. Los steps que ya estaban en COMPLETED no se repiten. Se ve en el cierre del 05: en el reinicio (ejecución 13) ya no aparece verificarArchivoStep, solo cargarMovimientosStep. Por la misma regla, el 31 de diciembre pude intentarlo tres veces (ejecuciones 14, 15 y 16): las tres fallaron y todas quedaron en la misma instancia 12.

3. En el reinicio del día 5, ¿por qué el step de carga leyó 10 movimientos y no 20?

Porque Spring Batch reanudó la lectura donde se había quedado. En la primera ejecución (la 12), el primer chunk de 10 movimientos se escribió y se confirmó (COMMIT_COUNT 1). El segundo chunk falló en el renglón 2001,DEPOSITO,mil, porque "mil" no se puede convertir a BigDecimal, y se hizo rollback (ROLLBACK_COUNT 1). En ese momento la tabla quedó con 10 filas.
En cada commit, Spring Batch guarda en el ExecutionContext del step cuántos elementos llevaba leídos el Lector. Al reiniciar con el archivo corregido (ejecución 13), el FlatFileItemReader usó ese dato, se saltó los 10 que ya estaban guardados y solo leyó y escribió los 10 restantes. Por eso la tabla terminó con 20 filas y no con 30: no se duplicó nada.

4. ¿Qué diferencia hay entre un movimiento **filtrado** y uno **omitido**?

- Filtrado: el renglón se leyó bien, pero el Procesador decidió que no debía escribirse y devolvió null. Es una regla de negocio. En mi caso descarta cualquier tipo que no sea DEPOSITO o RETIRO, como TRANSFERENCIA, PAGO o un tipo vacío. No es un error, no tiene límite y cuenta en FILTER_COUNT. El cierre del 04 lo muestra: leyó 20, filtró 2 y escribió 18.
- Omitido (skip): el renglón ni siquiera se pudo leer. El Lector lanzó una FlatFileParseException, por ejemplo con 3004,RETIRO,diez o con 3002;DEPOSITO;5.00, que usa punto y coma. Es un error que la política faultTolerant().skip(...) permite ignorar, pero con un tope: skipLimit(3). Cuenta en SKIP_COUNT.

5. ¿Por qué importa el código de salida, si el estado ya queda en las tablas?

Porque quien lanza el cierre normalmente no es una persona revisando MySQL, sino otro programa: un cron, un orquestador como Control-M o un pipeline. Ese programa no consulta BATCH_JOB_EXECUTION. Lo único que ve de inmediato es el número con el que terminó el proceso, y con eso decide si continúa con el siguiente proceso, si manda una alerta o si reintenta.
Mi evidencia muestra el problema. Antes del cambio en main (dia3-salida-antes), el cierre del 31 terminó en FAILED en las tablas, pero el programa salió con código 0. Para cualquier script, eso significa "todo bien", así que la falla habría pasado sin que nadie se enterara. Con System.exit(SpringApplication.exit(...)), el estado del Job se traduce a un código distinto de 0: el JAR sale con 5 (dia3-salida-jar), y a través de ./mvnw, Maven lo reporta como 1 (dia3-salida-despues). Las tablas sirven para investigar después qué pasó. El código de salida sirve para reaccionar en el momento.