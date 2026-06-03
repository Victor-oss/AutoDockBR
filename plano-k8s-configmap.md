# Migração para Kubernetes Jobs com ConfigMap (Sem S3/Redis AWS)

## **Arquitetura Simplificada**

```
┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
│  Spring Boot    │────▶│   K8s Job        │────▶│   ConfigMap     │
│  (local ou EKS) │     │   (AutoDock)     │     │   (resultado)   │
└────────┬────────┘     └──────────────────┘     └────────┬────────┘
         │                                                 │
         │              ┌──────────────────┐               │
         └─────────────▶│   PostgreSQL     │◀──────────────┘
                        │   (local)        │    (Spring lê e salva)
                        └──────────────────┘
```

**Fluxo:**

1. Usuário submete simulação → Spring Boot cria Job no K8s
2. Job executa docking no cluster EKS
3. Job cria ConfigMap com resultado (base64)
4. Spring Boot monitora Job → quando completo, lê ConfigMap
5. Spring Boot salva blob no PostgreSQL local
6. Spring Boot deleta ConfigMap e Job

**Limitação:** ConfigMaps suportam até ~1MB. Arquivos `.dlg` do AutoDock geralmente têm 50-500KB, então é suficiente.

---

## **FASE 1: Preparação AWS/EKS**

### 1.1 Criar Cluster EKS

```bash
export AWS_PROFILE=seu-profile
export AWS_REGION=us-east-2
export CLUSTER_NAME=autodock-cluster

eksctl create cluster \
  --name $CLUSTER_NAME \
  --region $AWS_REGION \
  --nodegroup-name autodock-nodes \
  --node-type c7i-flex.large \
  --nodes 3 \
  --nodes-min 1 \
  --nodes-max 3 \
  --managed
```

### 1.2 Atualizar kubeconfig

```bash
aws eks update-kubeconfig --region $AWS_REGION --name $CLUSTER_NAME
kubectl get nodes
```

### 1.3 Criar ECR e fazer push da imagem AutoDock

```bash
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

aws ecr create-repository --repository-name autodock --region $AWS_REGION

aws ecr get-login-password --region $AWS_REGION | \
docker login --username AWS --password-stdin $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

docker build -t autodock:4.2 -f src/main/docker/Dockerfile src/main/docker/
docker tag autodock:4.2 $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock:4.2
docker push $ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/autodock:4.2
```

### 1.4 Criar Namespace e RBAC

```bash
kubectl create namespace autodock

# Criar ServiceAccount com permissões para criar ConfigMaps
cat <<EOF | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: autodock-job
  namespace: autodock
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: configmap-creator
  namespace: autodock
rules:
- apiGroups: [""]
  resources: ["configmaps"]
  verbs: ["create", "get", "update", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: autodock-configmap-binding
  namespace: autodock
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: configmap-creator
subjects:
- kind: ServiceAccount
  name: autodock-job
  namespace: autodock
EOF
```

---

## **FASE 2: Atualizar Dockerfile do AutoDock**

### 2.1 Criar `src/main/docker/Dockerfile`

```dockerfile
FROM ubuntu:18.04

RUN apt-get update && apt-get install -y \
    build-essential \
    wget \
    unzip \
    python \
    python-numpy \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Instalar kubectl para criar ConfigMap
RUN curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" \
    && chmod +x kubectl && mv kubectl /usr/local/bin/

# Baixar AutoDock 4.2
RUN wget --no-check-certificate https://autodock.scripps.edu/wp-content/uploads/sites/56/2021/10/autodocksuite-4.2.6-x86_64Linux2.tar \
    && tar -xvf autodocksuite-4.2.6-x86_64Linux2.tar \
    && mv x86_64Linux2/autodock4 /usr/local/bin \
    && mv x86_64Linux2/autogrid4 /usr/local/bin \
    && chmod +x /usr/local/bin/autodock4 /usr/local/bin/autogrid4 \
    && rm -rf x86_64Linux2 autodocksuite-4.2.6-x86_64Linux2.tar

# Baixar MGLTools
RUN wget --no-check-certificate https://ccsb.scripps.edu/mgltools/download/491/ \
    -O mgltools.tar.gz \
    && tar -xvf mgltools.tar.gz \
    && mv mgltools_* /opt/mgltools \
    && tar -xvf /opt/mgltools/MGLToolsPckgs.tar.gz -C /opt/mgltools/ \
    && rm /opt/mgltools/MGLToolsPckgs.tar.gz \
    && rm mgltools.tar.gz

ENV LD_LIBRARY_PATH=/opt/mgltools/lib:$LD_LIBRARY_PATH
ENV PYTHONPATH=/opt/mgltools/MGLToolsPckgs
ENV PATH=$PATH:/usr/local/bin

WORKDIR /data
```

---

## **FASE 3: Modificações no Código Java**

### 3.1 Atualizar `pom.xml`

```xml
<!-- Kubernetes Client -->
<dependency>
    <groupId>io.kubernetes</groupId>
    <artifactId>client-java</artifactId>
    <version>18.0.0</version>
</dependency>
```

### 3.2 Atualizar `application.yml`???

```yaml
kubernetes:
  namespace: autodock
  autodock-image: ${AUTODOCK_IMAGE:123456789.dkr.ecr.us-east-2.amazonaws.com/autodock:4.2}
  # Para desenvolvimento local, use o kubeconfig do usuário
  # Em produção no EKS, usa ServiceAccount automaticamente
```

### 3.3 Criar `KubernetesJobService.java`

```java
package br.com.autodockbr.service;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import java.util.*;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class KubernetesJobService {

  private final Logger log = LoggerFactory.getLogger(KubernetesJobService.class);

  @Value("${kubernetes.namespace:autodock}")
  private String namespace;

  @Value("${kubernetes.autodock-image}")
  private String autodockImage;

  private ApiClient apiClient;
  private BatchV1Api batchApi;
  private CoreV1Api coreApi;

  @PostConstruct
  public void init() throws Exception {
    apiClient = Config.defaultClient();
    Configuration.setDefaultApiClient(apiClient);
    batchApi = new BatchV1Api();
    coreApi = new CoreV1Api();
    log.info("Kubernetes client initialized for namespace: {}", namespace);
  }

  /**
   * Submete um Job de docking para o cluster Kubernetes.
   * O Job recebe os arquivos PDB via ConfigMap de input e
   * salva o resultado em outro ConfigMap.
   */
  public String submitDockingJob(Long simulacaoId, byte[] receptorBytes, byte[] liganteBytes) throws Exception {
    String jobName = "docking-" + simulacaoId;
    String inputConfigMapName = "input-" + simulacaoId;
    String outputConfigMapName = "output-" + simulacaoId;

    // 1. Criar ConfigMap com os arquivos de input (base64)
    createInputConfigMap(inputConfigMapName, receptorBytes, liganteBytes);

    // 2. Criar o Job
    V1Job job = buildDockingJob(jobName, simulacaoId, inputConfigMapName, outputConfigMapName);
    batchApi.createNamespacedJob(namespace, job, null, null, null, null);

    log.info("Job {} created for simulacao {}", jobName, simulacaoId);
    return jobName;
  }

  private void createInputConfigMap(String name, byte[] receptorBytes, byte[] liganteBytes) throws ApiException {
    // Deletar se já existir
    try {
      coreApi.deleteNamespacedConfigMap(name, namespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      // Ignorar se não existir
    }

    V1ConfigMap configMap = new V1ConfigMap()
      .metadata(new V1ObjectMeta().name(name).namespace(namespace))
      .binaryData(Map.of("receptor.pdb", receptorBytes, "ligand.pdb", liganteBytes));

    coreApi.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
    log.debug("Created input ConfigMap: {}", name);
  }

  private V1Job buildDockingJob(String jobName, Long simulacaoId, String inputConfigMapName, String outputConfigMapName) {
    // Script que executa o docking e salva resultado em ConfigMap
    String script = String.join(
      "\n",
      "#!/bin/bash",
      "set -e",
      "cd /data",
      "",
      "# Copiar arquivos do ConfigMap montado",
      "cp /input/receptor.pdb .",
      "cp /input/ligand.pdb .",
      "",
      "export PYTHONPATH=/opt/mgltools/MGLToolsPckgs",
      "",
      "# Preparar receptor",
      "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_receptor4.py -r receptor.pdb -o receptor.pdbqt",
      "",
      "# Preparar ligante",
      "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_ligand4.py -l ligand.pdb -o ligand.pdbqt",
      "",
      "# Preparar grid parameter file",
      "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_gpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.gpf",
      "",
      "# Executar autogrid",
      "autogrid4 -p config.gpf -l autogrid.glg",
      "",
      "# Preparar docking parameter file",
      "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_dpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.dpf",
      "",
      "# Executar autodock",
      "autodock4 -p config.dpf -l resultado.dlg",
      "",
      "# Criar ConfigMap com o resultado (base64 encoded)",
      "RESULT_BASE64=$(base64 -w0 resultado.dlg)",
      "",
      "kubectl create configmap " + outputConfigMapName + " \\",
      "  --namespace=" + namespace + " \\",
      "  --from-literal=resultado.dlg.b64=\"$RESULT_BASE64\" \\",
      "  --from-literal=status=SUCCESS \\",
      "  --from-literal=simulacaoId=" + simulacaoId,
      "",
      "echo 'Docking completed successfully!'"
    );

    V1Container container = new V1Container()
      .name("autodock")
      .image(autodockImage)
      .imagePullPolicy("Always")
      .command(Arrays.asList("sh", "-c", script))
      .volumeMounts(
        Arrays.asList(
          new V1VolumeMount().name("input-data").mountPath("/input").readOnly(true),
          new V1VolumeMount().name("work-dir").mountPath("/data")
        )
      )
      .resources(
        new V1ResourceRequirements()
          .requests(
            Map.of("memory", new io.kubernetes.client.custom.Quantity("1Gi"), "cpu", new io.kubernetes.client.custom.Quantity("500m"))
          )
          .limits(
            Map.of("memory", new io.kubernetes.client.custom.Quantity("2Gi"), "cpu", new io.kubernetes.client.custom.Quantity("1000m"))
          )
      );

    V1PodSpec podSpec = new V1PodSpec()
      .serviceAccountName("autodock-job")
      .restartPolicy("Never")
      .containers(Collections.singletonList(container))
      .volumes(
        Arrays.asList(
          new V1Volume().name("input-data").configMap(new V1ConfigMapVolumeSource().name(inputConfigMapName)),
          new V1Volume().name("work-dir").emptyDir(new V1EmptyDirVolumeSource())
        )
      );

    return new V1Job()
      .apiVersion("batch/v1")
      .kind("Job")
      .metadata(
        new V1ObjectMeta().name(jobName).namespace(namespace).labels(Map.of("app", "autodock", "simulacaoId", String.valueOf(simulacaoId)))
      )
      .spec(
        new V1JobSpec()
          .backoffLimit(2)
          .ttlSecondsAfterFinished(3600) // Auto-delete após 1 hora
          .template(new V1PodTemplateSpec().metadata(new V1ObjectMeta().labels(Map.of("app", "autodock"))).spec(podSpec))
      );
  }

  /**
   * Verifica o status de um Job.
   * @return "RUNNING", "SUCCEEDED", "FAILED", ou "NOT_FOUND"
   */
  public String getJobStatus(String jobName) {
    try {
      V1Job job = batchApi.readNamespacedJob(jobName, namespace, null);
      V1JobStatus status = job.getStatus();

      if (status == null) {
        return "RUNNING";
      }
      if (status.getSucceeded() != null && status.getSucceeded() > 0) {
        return "SUCCEEDED";
      }
      if (status.getFailed() != null && status.getFailed() > 0) {
        return "FAILED";
      }
      return "RUNNING";
    } catch (ApiException e) {
      if (e.getCode() == 404) {
        return "NOT_FOUND";
      }
      log.error("Error checking job status: {}", jobName, e);
      return "ERROR";
    }
  }

  /**
   * Lê o resultado do ConfigMap de output.
   * @return bytes do arquivo resultado.dlg ou null se não encontrado
   */
  public byte[] getJobResult(Long simulacaoId) {
    String outputConfigMapName = "output-" + simulacaoId;
    try {
      V1ConfigMap configMap = coreApi.readNamespacedConfigMap(outputConfigMapName, namespace, null);
      Map<String, String> data = configMap.getData();

      if (data != null && data.containsKey("resultado.dlg.b64")) {
        String base64Result = data.get("resultado.dlg.b64");
        return Base64.getDecoder().decode(base64Result);
      }
      return null;
    } catch (ApiException e) {
      log.warn("Output ConfigMap not found for simulacao: {}", simulacaoId);
      return null;
    }
  }

  /**
   * Limpa os recursos do Job (ConfigMaps e Job).
   */
  public void cleanupJob(Long simulacaoId) {
    String jobName = "docking-" + simulacaoId;
    String inputConfigMapName = "input-" + simulacaoId;
    String outputConfigMapName = "output-" + simulacaoId;

    try {
      // Deletar Job
      batchApi.deleteNamespacedJob(jobName, namespace, null, null, null, null, "Background", null);
    } catch (ApiException e) {
      log.debug("Job {} already deleted or not found", jobName);
    }

    try {
      // Deletar ConfigMaps
      coreApi.deleteNamespacedConfigMap(inputConfigMapName, namespace, null, null, null, null, null, null);
      coreApi.deleteNamespacedConfigMap(outputConfigMapName, namespace, null, null, null, null, null, null);
    } catch (ApiException e) {
      log.debug("ConfigMaps already deleted or not found for simulacao: {}", simulacaoId);
    }

    log.info("Cleaned up resources for simulacao: {}", simulacaoId);
  }
}

```

### 3.4 Modificar `DockingAsyncService.java`

```java
package br.com.autodockbr.service;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import br.com.autodockbr.repository.SimulacaoRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DockingAsyncService {

  private final Logger log = LoggerFactory.getLogger(DockingAsyncService.class);

  private final SimulacaoRepository simulacaoRepository;
  private final KubernetesJobService kubernetesJobService;

  private static final int MAX_POLL_ATTEMPTS = 360; // 30 min máximo (5s * 360)
  private static final int POLL_INTERVAL_MS = 5000; // 5 segundos

  public DockingAsyncService(SimulacaoRepository simulacaoRepository, KubernetesJobService kubernetesJobService) {
    this.simulacaoRepository = simulacaoRepository;
    this.kubernetesJobService = kubernetesJobService;
  }

  @Async
  @Transactional
  public void startDockingAsync(Long simulacaoId, String workDirName, byte[] receptorBytes, byte[] liganteBytes) {
    log.info("Starting K8s docking job for simulacao: {}", simulacaoId);

    String jobName = null;
    try {
      // 1. Submeter Job para o Kubernetes
      jobName = kubernetesJobService.submitDockingJob(simulacaoId, receptorBytes, liganteBytes);

      // 2. Aguardar conclusão do Job (polling)
      boolean completed = waitForJobCompletion(jobName);

      if (completed) {
        // 3. Ler resultado do ConfigMap
        byte[] resultBytes = kubernetesJobService.getJobResult(simulacaoId);

        if (resultBytes != null && resultBytes.length > 0) {
          // 4. Salvar resultado no banco
          saveResult(simulacaoId, resultBytes);
          log.info("Docking completed successfully for simulacao: {}", simulacaoId);
        } else {
          log.error("No result found for simulacao: {}", simulacaoId);
          markAsError(simulacaoId);
        }
      } else {
        log.error("Job failed or timed out for simulacao: {}", simulacaoId);
        markAsError(simulacaoId);
      }
    } catch (Exception e) {
      log.error("Error during docking process for simulacao: {}", simulacaoId, e);
      markAsError(simulacaoId);
    } finally {
      // 5. Limpar recursos do K8s
      if (jobName != null) {
        try {
          kubernetesJobService.cleanupJob(simulacaoId);
        } catch (Exception e) {
          log.warn("Error cleaning up job resources for simulacao: {}", simulacaoId, e);
        }
      }
    }
  }

  private boolean waitForJobCompletion(String jobName) throws InterruptedException {
    for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
      String status = kubernetesJobService.getJobStatus(jobName);

      switch (status) {
        case "SUCCEEDED":
          return true;
        case "FAILED":
        case "NOT_FOUND":
        case "ERROR":
          return false;
        case "RUNNING":
        default:
          Thread.sleep(POLL_INTERVAL_MS);
      }
    }
    log.warn("Job {} timed out after {} attempts", jobName, MAX_POLL_ATTEMPTS);
    return false;
  }

  private void saveResult(Long simulacaoId, byte[] resultBytes) {
    Optional<Simulacao> optSimulacao = simulacaoRepository.findById(simulacaoId);
    if (optSimulacao.isPresent()) {
      Simulacao simulacao = optSimulacao.get();
      simulacao.setResultado(resultBytes);
      simulacao.setResultadoContentType("text/plain");
      simulacao.setTamanhoBytes((long) resultBytes.length);
      simulacao.setStatus(SimulacaoStatus.CONCLUIDO);
      simulacao.setDataHoraConclusao(Instant.now());
      simulacaoRepository.save(simulacao);
    }
  }

  private void markAsError(Long simulacaoId) {
    Optional<Simulacao> optSimulacao = simulacaoRepository.findById(simulacaoId);
    if (optSimulacao.isPresent()) {
      Simulacao simulacao = optSimulacao.get();
      simulacao.setStatus(SimulacaoStatus.ERRO);
      simulacao.setDataHoraConclusao(Instant.now());
      simulacaoRepository.save(simulacao);
    }
  }
}

```

---

## **FASE 4: Configuração do Acesso ao Cluster**

### 4.1 Para Desenvolvimento Local

O Spring Boot usa o `~/.kube/config` automaticamente:

```bash
# Garantir que o kubeconfig está configurado
aws eks update-kubeconfig --region us-east-2 --name autodock-cluster

# Testar conexão
kubectl get nodes
```

### 4.2 Para Produção (Spring Boot rodando no EKS)

Se o Spring Boot também rodar no EKS, usar ServiceAccount:

```yaml
# k8s/spring-boot-deployment.yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: autodockbr-app
  namespace: autodock
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: job-manager
  namespace: autodock
rules:
  - apiGroups: ['batch']
    resources: ['jobs']
    verbs: ['create', 'get', 'list', 'watch', 'delete']
  - apiGroups: ['']
    resources: ['configmaps']
    verbs: ['create', 'get', 'list', 'watch', 'delete']
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: autodockbr-job-manager
  namespace: autodock
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: Role
  name: job-manager
subjects:
  - kind: ServiceAccount
    name: autodockbr-app
    namespace: autodock
```

---

## **FASE 5: Testes**

### 5.1 Testar Job Manualmente

```bash
# Criar um ConfigMap de teste com arquivos PDB
kubectl create configmap test-input \
  --namespace=autodock \
  --from-file=receptor.pdb=./test-receptor.pdb \
  --from-file=ligand.pdb=./test-ligand.pdb

# Ver Jobs
kubectl get jobs -n autodock

# Ver logs do Job
kubectl logs job/docking-1001 -n autodock

# Ver ConfigMap de output
kubectl get configmap output-1001 -n autodock -o yaml
```

### 5.2 Verificar Resultado

```bash
# Decodificar resultado do ConfigMap
kubectl get configmap output-1001 -n autodock -o jsonpath='{.data.resultado\.dlg\.b64}' | base64 -d
```

---

## **FASE 6: Troubleshooting**

### Job fica Pending

```bash
kubectl describe job docking-1001 -n autodock
kubectl get events -n autodock --sort-by='.lastTimestamp'
kubectl describe pod -l job-name=docking-1001 -n autodock
```

### Job não consegue criar ConfigMap

```bash
# Verificar permissões do ServiceAccount
kubectl auth can-i create configmaps --as=system:serviceaccount:autodock:autodock-job -n autodock
```

### Spring Boot não conecta ao cluster

```bash
# Verificar kubeconfig
kubectl config current-context
kubectl cluster-info

# Testar via Java (logs)
# Procure por "Kubernetes client initialized" nos logs
```

---

## **RESUMO DAS MUDANÇAS**

| Arquivo                      | Mudança                                           |
| ---------------------------- | ------------------------------------------------- |
| `pom.xml`                    | +kubernetes-client                                |
| `application.yml`            | +kubernetes.namespace, +kubernetes.autodock-image |
| `DockingAsyncService.java`   | Substituir docker exec por K8s Job + polling      |
| `KubernetesJobService.java`  | NOVO - Criar/monitorar Jobs, ler ConfigMaps       |
| `src/main/docker/Dockerfile` | +kubectl para criar ConfigMap                     |

---

## **COMANDOS FINAIS**

```bash
# 1. Configurar cluster
aws eks update-kubeconfig --region us-east-2 --name autodock-cluster

# 2. Criar namespace e RBAC
kubectl create namespace autodock
kubectl apply -f k8s/rbac.yaml

# 3. Build e push da imagem AutoDock
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
docker build -t autodock:4.2 -f src/main/docker/Dockerfile src/main/docker/
docker tag autodock:4.2 $ACCOUNT_ID.dkr.ecr.us-east-2.amazonaws.com/autodock:4.2
docker push $ACCOUNT_ID.dkr.ecr.us-east-2.amazonaws.com/autodock:4.2

# 4. Rodar Spring Boot localmente
export AUTODOCK_IMAGE=$ACCOUNT_ID.dkr.ecr.us-east-2.amazonaws.com/autodock:4.2
./mvnw spring-boot:run

# 5. Monitorar jobs
watch kubectl get jobs,configmaps -n autodock
```
