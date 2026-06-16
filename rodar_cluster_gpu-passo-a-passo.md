# Rodar AutoDock-GPU 1.6 no Cluster EKS com GPU (g6f.large)

## Pré-requisitos

- AWS CLI configurado
- Docker instalado
- `eksctl` e `kubectl` instalados
- Conta AWS com permissão para criar EKS, ECR, e instâncias GPU

Listar profiles

aws configure list-profiles

## Variáveis de ambiente

```bash
export AWS_PROFILE=<profilecriado>
export AWS_REGION=us-east-2
export CLUSTER_NAME=autodock-cluster
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

> **Nota:** O build pode demorar pois compila o AutoDock-GPU com CUDA, mas **não exige GPU local**. A imagem base `nvidia/cuda:12.4.0-devel-ubuntu22.04` já inclui o compilador `nvcc` e os headers CUDA necessários para compilar. A GPU só é exigida em runtime, nos nós `g6f.large` do EKS.

## 2. Criar Cluster EKS com nós GPU (g6f.large)

```bash
eksctl create cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --nodegroup-name autodock-gpu-nodes \
  --node-type g6f.large \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

> **Nota:** Instâncias `g6f.large` possuem 1 GPU NVIDIA L40S Flex, 2 vCPUs e 16 GiB RAM.

## 3. Atualizar kubeconfig e verificar nós

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
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
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

kubectl create namespace monitoring

helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set grafana.adminPassword=admin123 \
  --set grafana.service.type=LoadBalancer
```

Aguardar o Grafana ficar disponível:

```bash
kubectl get pods -n monitoring

kubectl get svc -n monitoring monitoring-grafana
# Acesse o EXTERNAL-IP na porta 80 (user: admin / senha: admin)
```

## 8. Remover artefatos AWS

```bash
helm uninstall dcgm-exporter --namespace monitoring
helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring

kubectl delete namespace autodock

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
