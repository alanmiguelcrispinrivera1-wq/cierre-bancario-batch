#!/usr/bin/env bash
# Una consulta a tu base banco_db:   ./sql.sh "SELECT COUNT(*) FROM BATCH_JOB_INSTANCE"
docker exec -e MYSQL_PWD=academia123 banco-mysql mysql --default-character-set=utf8mb4 -uacademia banco_db -t -e "$1"
