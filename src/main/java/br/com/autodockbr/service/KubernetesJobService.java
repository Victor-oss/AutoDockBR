package br.com.autodockbr.service;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import java.nio.charset.StandardCharsets;
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

    public String submitDockingJob(Long simulacaoId, byte[] receptorBytes, byte[] liganteBytes) throws Exception {
        String jobName = "docking-" + simulacaoId;
        String inputConfigMapName = "input-" + simulacaoId;
        String outputConfigMapName = "output-" + simulacaoId;

        createInputConfigMap(inputConfigMapName, receptorBytes, liganteBytes);

        V1Job job = buildDockingJob(jobName, simulacaoId, inputConfigMapName, outputConfigMapName);
        batchApi.createNamespacedJob(namespace, job, null, null, null, null);

        log.info("Job {} created for simulacao {}", jobName, simulacaoId);
        return jobName;
    }

    private void createInputConfigMap(String name, byte[] receptorBytes, byte[] liganteBytes) throws ApiException {
        try {
            coreApi.deleteNamespacedConfigMap(name, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {}

        Map<String, byte[]> binaryData = new HashMap<>();
        binaryData.put("receptor.pdb", receptorBytes);
        binaryData.put("ligand.pdb", liganteBytes);

        V1ConfigMap configMap = new V1ConfigMap().metadata(new V1ObjectMeta().name(name).namespace(namespace)).binaryData(binaryData);

        coreApi.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
        log.debug("Created input ConfigMap: {}", name);
    }

    private V1Job buildDockingJob(String jobName, Long simulacaoId, String inputConfigMapName, String outputConfigMapName) {
        /*
        prepare_gpf4
         */
        String script = String.join(
            "\n",
            "#!/bin/bash",
            "set -e",
            "cd /data",
            "cp /input/receptor.pdb .",
            "cp /input/ligand.pdb .",
            "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_receptor4.py -r receptor.pdb -o receptor.pdbqt",
            "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_ligand4.py -l ligand.pdb -o ligand.pdbqt",
            "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_gpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.gpf",
            "autogrid4 -p config.gpf -l autogrid.glg",
            "python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_dpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.dpf",
            "autodock4 -p config.dpf -l resultado.dlg",
            "kubectl create configmap " + outputConfigMapName + " \\",
            "  --namespace=" + namespace + " \\",
            "  --from-file=resultado.dlg=resultado.dlg \\",
            "  --from-literal=status=SUCCESS \\",
            "  --from-literal=simulacaoId=" + simulacaoId,
            "",
            "echo 'Docking completed successfully!'"
        );

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("memory", new Quantity("1Gi"));
        requests.put("cpu", new Quantity("500m"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("memory", new Quantity("2Gi"));
        limits.put("cpu", new Quantity("1000m"));

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
            .resources(new V1ResourceRequirements().requests(requests).limits(limits));

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

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "autodock");
        labels.put("simulacaoId", String.valueOf(simulacaoId));

        return new V1Job()
            .apiVersion("batch/v1")
            .kind("Job")
            .metadata(new V1ObjectMeta().name(jobName).namespace(namespace).labels(labels))
            .spec(
                new V1JobSpec()
                    .backoffLimit(2)
                    .ttlSecondsAfterFinished(3600)
                    .template(
                        new V1PodTemplateSpec()
                            .metadata(new V1ObjectMeta().labels(Collections.singletonMap("app", "autodock")))
                            .spec(podSpec)
                    )
            );
    }

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

    public byte[] getJobResult(Long simulacaoId) {
        String outputConfigMapName = "output-" + simulacaoId;
        try {
            V1ConfigMap configMap = coreApi.readNamespacedConfigMap(outputConfigMapName, namespace, null);
            Map<String, String> data = configMap.getData();

            if (data != null && data.containsKey("resultado.dlg")) {
                return data.get("resultado.dlg").getBytes(StandardCharsets.UTF_8);
            }
            return null;
        } catch (ApiException e) {
            log.warn("Output ConfigMap not found for simulacao: {}", simulacaoId);
            return null;
        }
    }

    public void cleanupJob(Long simulacaoId) {
        String jobName = "docking-" + simulacaoId;
        String inputConfigMapName = "input-" + simulacaoId;
        String outputConfigMapName = "output-" + simulacaoId;

        try {
            batchApi.deleteNamespacedJob(jobName, namespace, null, null, null, null, "Background", null);
        } catch (ApiException e) {
            log.debug("Job {} already deleted or not found", jobName);
        }

        try {
            coreApi.deleteNamespacedConfigMap(inputConfigMapName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.debug("Input ConfigMap already deleted or not found for simulacao: {}", simulacaoId);
        }

        try {
            coreApi.deleteNamespacedConfigMap(outputConfigMapName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.debug("Output ConfigMap already deleted or not found for simulacao: {}", simulacaoId);
        }

        log.info("Cleaned up resources for simulacao: {}", simulacaoId);
    }
}
