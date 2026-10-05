#!/usr/bin/env bash
# Corre el cierre de UNA fecha:   ./correr.sh 2026-09-28 dia1-primer-job
# Es lo mismo que escribir:       ./mvnw -q spring-boot:run -Dspring-boot.run.arguments="fecha=2026-09-28"
# La salida COMPLETA queda en evidencia/<nombre>.txt. En pantalla salen solo las líneas que dicen qué pasó (los steps,
# tus líneas «>>>», el resultado del Job, las excepciones, los errores de compilación y, si Spring Boot no arranca, su
# explicación), sin la hora ni la clase que escribió cada línea. Si el programa falla y nada de eso lo explica, muestra
# las últimas líneas de la salida completa.
set -u
uso() { echo "Uso: ./correr.sh <fecha AAAA-MM-DD> <nombre-de-la-evidencia>     Ejemplo: ./correr.sh 2026-09-28 dia1-primer-job"; exit 2; }
[ $# -eq 2 ] || uso
[[ $1 =~ ^[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$ ]] || uso
[[ $2 =~ ^[A-Za-z0-9_-]+$ ]] || { echo "El nombre de la evidencia solo puede llevar letras, números, - y _ (sin espacios ni /)."; uso; }
mkdir -p evidencia
SALIDA="evidencia/$2.txt"
if [ -e "$SALIDA" ]; then
  echo "Ya existe $SALIDA y no la voy a sobrescribir."
  echo "Si el intento anterior falló y quieres repetirlo con el mismo nombre, bórrala primero:  rm $SALIDA"
  exit 2
fi
echo "\$ ./mvnw -q spring-boot:run -Dspring-boot.run.arguments=\"fecha=$1\"" > "$SALIDA"
./mvnw -q spring-boot:run -Dspring-boot.run.arguments="fecha=$1" >> "$SALIDA" 2>&1
codigo=$?
echo "Código de salida: $codigo" >> "$SALIDA"

patron='>>>|Application run failed|Executing step:|Step already complete|Job: \[|Encountered an error executing step|^Caused by: |^[a-z][A-Za-z0-9_.]*(Exception|Error): |^\[ERROR\] .*\.java:\[[0-9]+,[0-9]+\]|^(\[ERROR\])? +(symbol|location|required|found|reason): |^Código de salida: '
# Maven repite los errores de compilación después de «Failed to execute goal»: de ahí en adelante solo la última línea.
fin=$(grep -n -m1 'Failed to execute goal' "$SALIDA" | cut -d: -f1); fin=${fin:-999999}
vista=$( {
  grep -nE "$patron" "$SALIDA" | awk -F: -v f="$fin" '$1 < f || /^[0-9]+:Código de salida: /'
  awk '/^APPLICATION FAILED TO START$/ {b=1} b && NF && !/^\*+$/ {print NR ":" $0} /^Action:/ {b=0}' "$SALIDA"
} | sort -t: -k1,1n -u )
printf '%s\n' "$vista" | cut -d: -f2- | sed -E 's/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[^ ]+ +[A-Z]+ [0-9]+ --- \[[^]]*\] \[[^]]*\] [^ ]+ +: //'
total=$(wc -l < "$SALIDA"); mostradas=$(printf '%s\n' "$vista" | grep -c .)
if [ "$codigo" -ne 0 ] && [ "$mostradas" -le 1 ]; then
  echo "No reconocí la causa. Las últimas líneas de $SALIDA (sin la ayuda genérica de Maven):"
  grep -vE '^\[ERROR\] *($|-> \[Help|To see the full|Re-run Maven|For more information|\[Help [0-9])' "$SALIDA" | tail -12
fi
echo "($((total - mostradas)) líneas más —y la hora y la clase de cada línea— en $SALIDA)"
exit $codigo
