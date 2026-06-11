#!/bin/bash
# stress-test.sh — submete N jobs de docking idênticos
set -euo pipefail

NAMESPACE="autodock"
IMAGE="<SEU_ACCOUNT_ID>.dkr.ecr.us-east-2.amazonaws.com/autodock:4.2"
N=${1:-10}

# Cria ConfigMap com os PDB fixos (uma vez)
kubectl delete configmap stress-input -n $NAMESPACE --ignore-not-found
kubectl create configmap stress-input -n $NAMESPACE \
  --from-file=receptor.pdb=./1HVR.pdb \
  --from-file=ligand.pdb=./XK2-ligand.pdb

echo "Submetendo $N jobs..."
START=$(date +%s)

for i in $(seq 1 $N); do
cat <<EOF | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: stress-docking-$i
  namespace: $NAMESPACE
  labels:
    app: autodock
    test: stress
spec:
  backoffLimit: 2
  ttlSecondsAfterFinished: 600
  template:
    metadata:
      labels:
        app: autodock
        test: stress
    spec:
      serviceAccountName: autodock-job
      restartPolicy: Never
      containers:
      - name: autodock
        image: $IMAGE
        imagePullPolicy: Always
        command: ["sh", "-c"]
        args:
        - |
          cd /data
          cp /input/receptor.pdb .
          cp /input/ligand.pdb .
          python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_receptor4.py -r receptor.pdb -o receptor.pdbqt
          python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_ligand4.py -l ligand.pdb -o ligand.pdbqt
          python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_gpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.gpf
          autogrid4 -p config.gpf -l autogrid.glg
          python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_dpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.dpf
          autodock4 -p config.dpf -l resultado.dlg
          echo "Done"
        resources:
          requests:
            memory: "1Gi"
            cpu: "600m"
          limits:
            memory: "2Gi"
            cpu: "600m"
        volumeMounts:
        - name: input-data
          mountPath: /input
          readOnly: true
        - name: work-dir
          mountPath: /data
      volumes:
      - name: input-data
        configMap:
          name: stress-input
      - name: work-dir
        emptyDir: {}
EOF
done

END=$(date +%s)
echo "Todos os $N jobs submetidos em $((END - START))s"
echo ""
echo "Monitorar com:"
echo "  watch kubectl get jobs -n $NAMESPACE -l test=stress"
echo "  kubectl get pods -n $NAMESPACE -l test=stress --field-selector=status.phase=Pending"