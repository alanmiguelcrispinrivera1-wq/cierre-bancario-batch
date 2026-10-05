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
