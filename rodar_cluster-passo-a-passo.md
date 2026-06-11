Para isso, criei um profile de um usuário da AWS e defini ele como o padrão no meu terminal

Listar profiles

aws configure list-profiles

Pegar id da conta

aws sts get-caller-identity

export AWS_PROFILE=<profilecriado>
export AWS_REGION=us-east-2
export CLUSTER_NAME=autodock-cluster

1 Criar repositório no Amazon ECR e fazer push da imagem

```bash
aws ecr create-repository --repository-name autodock --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION | \
docker login --username AWS --password-stdin <SEU_ACCOUNT_ID>.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -t autodock:4.2 -f src/main/docker/Dockerfile src/main/docker/

docker tag autodock:4.2 <SEU_ACCOUNT_ID>.dkr.ecr.$AWS_REGION.amazonaws.com/autodock:4.2

docker push <SEU_ACCOUNT_ID>.dkr.ecr.$AWS_REGION.amazonaws.com/autodock:4.2
```

2 Criar Cluster EKS

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

3 Atualizar kubeconfig

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

Resultado esperado:

```text
ip-xxx   Ready
ip-yyy   Ready
```

4 Criar Namespace e RBAC

```bash
kubectl create namespace autodock

kubectl apply -f k8s/rbac.yaml
```

6 Rodar docker

6.5 Adicionar monitoramento Grafana + Prometheus

```bash
# Instalar Helm (se necessário)
curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash

# Adicionar repositório e instalar kube-prometheus-stack
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

Ou via port-forward:

```bash
kubectl port-forward -n monitoring svc/monitoring-grafana 3000:80
# Acesse http://localhost:3000
```

5 Variávei de ambiente antes de rodar o Spring Boot

```bash
export KUBERNETES_NAMESPACE=autodock
export AUTODOCK_IMAGE=<IMG>
script log.txt
```

7 Remover artefatos AWS

# 0. Remover monitoramento

helm uninstall monitoring --namespace monitoring
kubectl delete namespace monitoring

# 1. Remover recursos Kubernetes (namespace apaga tudo dentro: RBAC, jobs, configmaps)

kubectl delete namespace autodock

# 2. Deletar o cluster EKS (apaga nodes, node groups, VPC criada pelo eksctl)

eksctl delete cluster \
 --name $CLUSTER_NAME \
 --region $AWS_REGION

# 3. Deletar repositório ECR (--force apaga mesmo com imagens)

aws ecr delete-repository \
 --repository-name autodock \
 --region $AWS_REGION \
 --force

aws cloudformation delete-stack \
 --stack-name eksctl-autodock-cluster-cluster \
 --region $AWS_REGION \
 --deletion-mode FORCE_DELETE_STACK

aws cloudformation wait stack-delete-complete \
 --stack-name eksctl-autodock-cluster-cluster \
 --region $AWS_REGION

Debug

kubectl get pods -n autodock

kubectl logs nome-pod -n autodock
