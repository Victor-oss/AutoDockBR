# Rodar AutoDock 4.2.6 no Cluster EKS sem GPU (m7i-flex.large)

## Pré-requisitos

- AWS CLI configurado
- Docker instalado
- `eksctl` e `kubectl` instalados
- Conta AWS com permissão para criar EKS, ECR, e instâncias

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
export AUTODOCK_IMAGE=$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock:4.2
```

## 1. Criar repositório no Amazon ECR e fazer push da imagem com AutoDock 4.2.6

```bash
aws ecr create-repository --repository-name autodock --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION | \
docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -t autodock:4.2 -f src/main/docker/Dockerfile src/main/docker/

docker tag autodock:4.2 $AUTODOCK_IMAGE

docker push $AUTODOCK_IMAGE
```

## 2. Criar Cluster EKS com nós sem GPU (m7i-flex.large)

```bash
eksctl create cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --nodegroup-name autodock-nodes \
  --node-type m7i-flex.large \
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

## 4. Criar Namespace e RBAC

```bash
kubectl create namespace autodock

kubectl apply -f k8s/rbac.yaml
```

## 5. Variáveis de ambiente para o Spring Boot

```bash
export KUBERNETES_NAMESPACE=autodock
```

## 6. Monitoramento (opcional) - Grafana + Prometheus

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

## 7. Suba a aplicação local se baseando no README.md

## 8. Remover artefatos AWS

### Caso seja teste de estresse, use o comando abaixo para remover os jobs:

```
kubectl delete jobs -n autodock -l test=stress
kubectl delete configmap stress-input -n autodock
```

### Para remover o monitoramento:

```bash
helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring
```

### Remover recursos Kubernetes (namespace apaga tudo dentro: RBAC, jobs, configmaps):

```bash
kubectl delete namespace autodock
```

### Deletar o cluster EKS (apaga nodes, node groups, VPC criada pelo eksctl):

```bash
eksctl delete cluster \
 --name $CLUSTER_NAME \
 --region $AWS_REGION
```

### Deletar repositório ECR (--force apaga mesmo com imagens):

```bash
aws ecr delete-repository \
 --repository-name autodock \
 --region $AWS_REGION \
 --force
```

### Deletar stacks:

```bash
aws cloudformation delete-stack \
 --stack-name eksctl-autodock-cluster-cluster \
 --region $AWS_REGION \
 --deletion-mode FORCE_DELETE_STACK

aws cloudformation wait stack-delete-complete \
 --stack-name eksctl-autodock-cluster-cluster \
 --region $AWS_REGION
```
