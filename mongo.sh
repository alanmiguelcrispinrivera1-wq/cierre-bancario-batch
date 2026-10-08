#!/usr/bin/env bash
# Una consulta a tu base banco_db de MongoDB:   ./mongo.sh 'db.saldos.countDocuments()'
docker exec banco-mongo mongosh --quiet -u academia -p academia123 --authenticationDatabase admin banco_db --eval "$1"