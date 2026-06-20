#!/bin/bash
# monitor-stress.sh — coleta métricas durante o teste
NAMESPACE="autodock-cluster"

while true; do
  TOTAL=$(kubectl get jobs -n $NAMESPACE -l test=stress --no-headers | wc -l)
  SUCCEEDED=$(kubectl get jobs -n $NAMESPACE -l test=stress --no-headers | grep -c "1/1" || true)
  FAILED=$(kubectl get jobs -n $NAMESPACE -l test=stress --no-headers | grep -c "0/1.*BackoffLimitExceeded" || true)
  PENDING=$(kubectl get pods -n $NAMESPACE -l test=stress --field-selector=status.phase=Pending --no-headers | wc -l)
  RUNNING=$(kubectl get pods -n $NAMESPACE -l test=stress --field-selector=status.phase=Running --no-headers | wc -l)

  echo "$(date +%H:%M:%S) | Total: $TOTAL | Running: $RUNNING | Pending: $PENDING | Succeeded: $SUCCEEDED | Failed: $FAILED"
  sleep 10
done