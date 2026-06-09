# Rodar AutoDock-GPU 1.6 no Cluster EKS com GPU (g6f.large)

## Pré-requisitos

- AWS CLI configurado
- Docker instalado
- `eksctl` e `kubectl` instalados
- Conta AWS com permissão para criar EKS, ECR, e instâncias GPU

## Variáveis de ambiente

```bash
export AWS_PROFILE=<profilecriado>
export AWS_REGION=us-east-2
export CLUSTER_NAME=autodock-gpu-cluster
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
```

## 1. Criar repositório no Amazon ECR e fazer push da imagem GPU

```bash
aws ecr create-repository --repository-name autodock-gpu --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION | \
docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -t autodock-gpu:1.6 -f src/main/docker/Dockerfile.gpu src/main/docker/

docker tag autodock-gpu:1.6 $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock-gpu:1.6

docker push $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock-gpu:1.6
```

> **Nota:** O build pode demorar pois compila o AutoDock-GPU com CUDA. Requer uma máquina com NVIDIA GPU + CUDA toolkit para build, ou use `docker buildx` com `--platform linux/amd64`.

## 2. Criar Cluster EKS com nós GPU (g6f.large)

```bash
eksctl create cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --nodegroup-name autodock-gpu-nodes \
  --node-type g6f.large \
  --nodes 2 \
  --nodes-min 1 \
  --nodes-max 4 \
  --managed
```

> **Nota:** Instâncias `g6f.large` possuem 1 GPU NVIDIA L40S Flex, 2 vCPUs e 16 GiB RAM.

## 3. Atualizar kubeconfig e verificar nós

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

## 4. Instalar NVIDIA Device Plugin para Kubernetes

O device plugin permite que os pods solicitem GPUs via `nvidia.com/gpu`.

```bash
kubectl apply -f https://raw.githubusercontent.com/NVIDIA/k8s-device-plugin/v0.17.0/deployments/static/nvidia-device-plugin.yml
```

Verificar que o plugin está rodando:

```bash
kubectl get pods -n kube-system -l name=nvidia-device-plugin-ds
```

Verificar GPUs disponíveis nos nós:

```bash
kubectl describe nodes | grep -A5 "nvidia.com/gpu"
```

## 5. Criar Namespace e RBAC

```bash
kubectl create namespace autodock

kubectl apply -f k8s/rbac.yaml
```

## 6. Variáveis de ambiente para o Spring Boot

```bash
export KUBERNETES_NAMESPACE=autodock
export AUTODOCK_GPU_IMAGE=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock-gpu:1.6
```

## 7. Monitoramento (opcional) - Grafana + Prometheus

```bash
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

kubectl create namespace monitoring

helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set grafana.adminPassword=admin123 \
  --set grafana.service.type=LoadBalancer
```

Para monitorar métricas de GPU, instale o DCGM Exporter:

```bash
helm repo add gpu-helm-charts https://nvidia.github.io/dcgm-exporter/helm-charts
helm repo update

helm install dcgm-exporter gpu-helm-charts/dcgm-exporter \
  --namespace monitoring \
  --set serviceMonitor.enabled=true
```

Acessar Grafana:

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
# http://localhost:3000 (user: admin / senha: admin123)
```

## 8. Remover artefatos AWS

```bash
# Remover monitoramento
helm uninstall dcgm-exporter --namespace monitoring
helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring

# Remover namespace autodock (apaga RBAC, jobs, configmaps)
kubectl delete namespace autodock

# Deletar cluster EKS
eksctl delete cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION

# Deletar repositório ECR
aws ecr delete-repository \
  --repository-name autodock-gpu \
  --region $AWS_REGION \
  --force
```

## Debug

```bash
kubectl get pods -n autodock
kubectl logs <nome-pod> -n autodock
kubectl describe pod <nome-pod> -n autodock

# Verificar se GPU foi alocada ao pod
kubectl exec -it <nome-pod> -n autodock -- nvidia-smi
```
