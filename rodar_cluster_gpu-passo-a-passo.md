# Rodar AutoDock-GPU 1.6 no Cluster EKS com GPU (g6f.large)

## Pré-requisitos

- AWS CLI configurado
- Docker instalado
- `eksctl` e `kubectl` instalados
- Conta AWS com permissão para criar EKS, ECR, e instâncias GPU

## Comando para listar profiles

```bash
aws configure list-profiles
```

## Variáveis de ambiente

```bash
export AWS_PROFILE=<profilecriado>
export AWS_REGION=us-east-2
export CLUSTER_NAME=autodock-cluster
export ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export AUTODOCK_GPU_IMAGE=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock-gpu:1.6
```

## 1. Criar repositório no Amazon ECR e fazer push da imagem GPU

```bash
aws ecr create-repository --repository-name autodock-gpu --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION | \
docker login --username AWS --password-stdin $AUTODOCK_GPU_IMAGE

docker build -t autodock-gpu:1.6 -f src/main/docker/Dockerfile.gpu src/main/docker/

docker tag autodock-gpu:1.6 $AUTODOCK_GPU_IMAGE

docker push $AUTODOCK_GPU_IMAGE
```

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

## 3. Atualizar kubeconfig e verificar nós

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

## 4. Verificar se o plugin está rodando:

```bash
kubectl get pods -n kube-system -l name=nvidia-device-plugin-ds
```

## 5. Verificar GPUs disponíveis nos nós:

```bash
kubectl describe nodes | grep -A5 "nvidia.com/gpu"
```

## 6. Criar Namespace e RBAC

```bash
kubectl create namespace autodock

kubectl apply -f k8s/rbac.yaml
```

## 7. Variáveis de ambiente para o Spring Boot

```bash
export KUBERNETES_NAMESPACE=autodock
```

## 8. Monitoramento (opcional) - Grafana + Prometheus

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

kubectl create namespace monitoring

helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set grafana.adminPassword=admin123 \
  --set grafana.service.type=LoadBalancer
```

Aguarde o Grafana ficar disponível:

```bash
kubectl get pods -n monitoring

kubectl get svc -n monitoring monitoring-grafana
# Acesse o EXTERNAL-IP na porta 80 (user: admin / senha: admin123)
```

## 9. Remover artefatos AWS

```bash
helm uninstall dcgm-exporter --namespace monitoring
helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring

kubectl delete namespace autodock

eksctl delete cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION

aws ecr delete-repository \
  --repository-name autodock-gpu \
  --region $AWS_REGION \
  --force
```
