#!/usr/bin/env bash
# Lo que Spring Batch anotó en MySQL.   ./ver-batch.sh             → todas las fechas
#                                       ./ver-batch.sh 2026-09-28  → solo esa fecha
set -u
donde=""
[ $# -ge 1 ] && donde="WHERE p.PARAMETER_VALUE = '$1'"
consulta() { docker exec -e MYSQL_PWD=academia123 banco-mysql mysql --default-character-set=utf8mb4 -uacademia banco_db -t -e "$1"; }
echo "== Instancias y ejecuciones del Job (BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION, BATCH_JOB_EXECUTION_PARAMS)"
consulta "SELECT e.JOB_INSTANCE_ID, e.JOB_EXECUTION_ID, p.PARAMETER_VALUE AS fecha, e.STATUS, e.EXIT_CODE
          FROM BATCH_JOB_EXECUTION e
          LEFT JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = e.JOB_EXECUTION_ID AND p.PARAMETER_NAME = 'fecha'
          $donde ORDER BY e.JOB_EXECUTION_ID"
echo "== Ejecuciones de cada step (BATCH_STEP_EXECUTION)"
consulta "SELECT s.JOB_EXECUTION_ID, p.PARAMETER_VALUE AS fecha, s.STEP_NAME, s.STATUS, s.READ_COUNT, s.FILTER_COUNT,
                 s.WRITE_COUNT, s.READ_SKIP_COUNT + s.PROCESS_SKIP_COUNT + s.WRITE_SKIP_COUNT AS SKIP_COUNT,
                 s.COMMIT_COUNT, s.ROLLBACK_COUNT
          FROM BATCH_STEP_EXECUTION s
          LEFT JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = s.JOB_EXECUTION_ID AND p.PARAMETER_NAME = 'fecha'
          $donde ORDER BY s.STEP_EXECUTION_ID"
fallas=$(consulta "SELECT COUNT(*) AS n FROM BATCH_STEP_EXECUTION s
          LEFT JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = s.JOB_EXECUTION_ID AND p.PARAMETER_NAME = 'fecha'
          $donde $([ -n "$donde" ] && echo AND || echo WHERE) s.STATUS = 'FAILED'" | grep -oE '[0-9]+' | head -1)
if [ "${fallas:-0}" != "0" ]; then
  echo "== Por qué falló (la primera línea de EXIT_MESSAGE de cada step FAILED)"
  consulta "SELECT s.JOB_EXECUTION_ID, s.STEP_NAME, SUBSTRING_INDEX(s.EXIT_MESSAGE, '\n', 1) AS EXIT_MESSAGE
            FROM BATCH_STEP_EXECUTION s
            LEFT JOIN BATCH_JOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = s.JOB_EXECUTION_ID AND p.PARAMETER_NAME = 'fecha'
            $donde $([ -n "$donde" ] && echo AND || echo WHERE) s.STATUS = 'FAILED' ORDER BY s.STEP_EXECUTION_ID"
fi
