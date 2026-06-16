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
public class KubernetesGpuJobService {

    private final Logger log = LoggerFactory.getLogger(KubernetesGpuJobService.class);

    @Value("${kubernetes.namespace:autodock}")
    private String namespace;

    @Value("${kubernetes.autodock-gpu-image}")
    private String autodockGpuImage;

    private ApiClient apiClient;
    private BatchV1Api batchApi;
    private CoreV1Api coreApi;

    @PostConstruct
    public void init() throws Exception {
        apiClient = Config.defaultClient();
        Configuration.setDefaultApiClient(apiClient);
        batchApi = new BatchV1Api();
        coreApi = new CoreV1Api();
        log.info("Kubernetes GPU client initialized for namespace: {}", namespace);
    }

    public String submitGpuDockingJob(Long simulacaoId, byte[] receptorBytes, byte[] liganteBytes) throws Exception {
        String jobName = "docking-gpu-" + simulacaoId;
        String inputConfigMapName = "input-gpu-" + simulacaoId;
        String outputConfigMapName = "output-gpu-" + simulacaoId;

        createInputConfigMap(inputConfigMapName, receptorBytes, liganteBytes);

        V1Job job = buildGpuDockingJob(jobName, simulacaoId, inputConfigMapName, outputConfigMapName);
        batchApi.createNamespacedJob(namespace, job, null, null, null, null);

        log.info("GPU Job {} created for simulacao {}", jobName, simulacaoId);
        return jobName;
    }

    private void createInputConfigMap(String name, byte[] receptorBytes, byte[] liganteBytes) throws ApiException {
        try {
            coreApi.deleteNamespacedConfigMap(name, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            // ConfigMap does not exist yet, ignore
        }

        Map<String, byte[]> binaryData = new HashMap<>();
        binaryData.put("receptor.pdb", receptorBytes);
        binaryData.put("ligand.pdb", liganteBytes);

        V1ConfigMap configMap = new V1ConfigMap().metadata(new V1ObjectMeta().name(name).namespace(namespace)).binaryData(binaryData);

        coreApi.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
        log.debug("Created input ConfigMap: {}", name);
    }

    private V1Job buildGpuDockingJob(String jobName, Long simulacaoId, String inputConfigMapName, String outputConfigMapName) {
        String script = String.join(
            "\n",
            "#!/bin/bash",
            "set -e",
            "cd /data",
            "",
            "# Copiar arquivos de entrada",
            "cp /input/receptor.pdb .",
            "cp /input/ligand.pdb .",
            "",
            "# Preparar receptor: PDB -> PDBQT",
            "prepare_receptor -r receptor.pdb -o receptor.pdbqt",
            "",
            "# Preparar ligante: PDB -> PDBQT",
            "prepare_ligand -l ligand.pdb -o ligand.pdbqt",
            "",
            "# Gerar grid parameter file (.gpf) via script Python",
            "python3 -c \"",
            "import numpy as np",
            "import re",
            "",
            "# Ler coordenadas do ligante para centralizar o grid",
            "coords = []",
            "with open('ligand.pdbqt') as f:",
            "    for line in f:",
            "        if line.startswith('ATOM') or line.startswith('HETATM'):",
            "            coords.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])",
            "coords = np.array(coords)",
            "center = coords.mean(axis=0)",
            "size = coords.ptp(axis=0) + 10.0  # margem de 10A",
            "",
            "# Ler tipos de atomo do ligante",
            "atypes = set()",
            "with open('ligand.pdbqt') as f:",
            "    for line in f:",
            "        if line.startswith('ATOM') or line.startswith('HETATM'):",
            "            atypes.add(line[77:79].strip())",
            "",
            "with open('config.gpf', 'w') as gpf:",
            "    gpf.write('npts %d %d %d\\n' % (int(size[0]/0.375), int(size[1]/0.375), int(size[2]/0.375)))",
            "    gpf.write('gridfld receptor.maps.fld\\n')",
            "    gpf.write('spacing 0.375\\n')",
            "    gpf.write('receptor_types A C HD N NA OA SA\\n')",
            "    gpf.write('ligand_types %s\\n' % ' '.join(sorted(atypes)))",
            "    gpf.write('receptor receptor.pdbqt\\n')",
            "    gpf.write('gridcenter %.3f %.3f %.3f\\n' % (center[0], center[1], center[2]))",
            "    gpf.write('smooth 0.5\\n')",
            "    for at in sorted(atypes):",
            "        gpf.write('map receptor.%s.map\\n' % at)",
            "    gpf.write('elecmap receptor.e.map\\n')",
            "    gpf.write('dsolvmap receptor.d.map\\n')",
            "    gpf.write('dielectric -0.1465\\n')",
            "print('GPF generated: center=(%.2f,%.2f,%.2f) size=(%.1f,%.1f,%.1f)' % (*center, *size))",
            "\"",
            "",
            "# Rodar autogrid4 para gerar os mapas (.fld)",
            "autogrid4 -p config.gpf -l autogrid.glg",
            "",
            "# Rodar AutoDock-GPU com o .maps.fld gerado pelo autogrid",
            "autodock_gpu_128wi -ffile receptor.maps.fld -lfile ligand.pdbqt -nrun 100 -resnam resultado",
            "",
            "# Salvar resultado no ConfigMap de saída",
            "kubectl create configmap " + outputConfigMapName + " \\",
            "  --namespace=" + namespace + " \\",
            "  --from-file=resultado.dlg=resultado.dlg \\",
            "  --from-literal=status=SUCCESS \\",
            "  --from-literal=simulacaoId=" + simulacaoId,
            "",
            "echo 'GPU Docking completed successfully!'"
        );

        Map<String, Quantity> requests = new HashMap<>();
        requests.put("memory", new Quantity("2Gi"));
        requests.put("cpu", new Quantity("1000m"));
        requests.put("nvidia.com/gpu", new Quantity("1"));

        Map<String, Quantity> limits = new HashMap<>();
        limits.put("memory", new Quantity("4Gi"));
        limits.put("cpu", new Quantity("2000m"));
        limits.put("nvidia.com/gpu", new Quantity("1"));

        V1Container container = new V1Container()
            .name("autodock-gpu")
            .image(autodockGpuImage)
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
            .tolerations(Collections.singletonList(new V1Toleration().key("nvidia.com/gpu").operator("Exists").effect("NoSchedule")))
            .volumes(
                Arrays.asList(
                    new V1Volume().name("input-data").configMap(new V1ConfigMapVolumeSource().name(inputConfigMapName)),
                    new V1Volume().name("work-dir").emptyDir(new V1EmptyDirVolumeSource())
                )
            );

        Map<String, String> labels = new HashMap<>();
        labels.put("app", "autodock-gpu");
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
                            .metadata(new V1ObjectMeta().labels(Collections.singletonMap("app", "autodock-gpu")))
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
            log.error("Error checking GPU job status: {}", jobName, e);
            return "ERROR";
        }
    }

    public byte[] getJobResult(Long simulacaoId) {
        String outputConfigMapName = "output-gpu-" + simulacaoId;
        try {
            V1ConfigMap configMap = coreApi.readNamespacedConfigMap(outputConfigMapName, namespace, null);
            Map<String, String> data = configMap.getData();

            if (data != null && data.containsKey("resultado.dlg")) {
                return data.get("resultado.dlg").getBytes(StandardCharsets.UTF_8);
            }
            return null;
        } catch (ApiException e) {
            log.warn("Output ConfigMap not found for GPU simulacao: {}", simulacaoId);
            return null;
        }
    }

    public void cleanupJob(Long simulacaoId) {
        String jobName = "docking-gpu-" + simulacaoId;
        String inputConfigMapName = "input-gpu-" + simulacaoId;
        String outputConfigMapName = "output-gpu-" + simulacaoId;

        try {
            batchApi.deleteNamespacedJob(jobName, namespace, null, null, null, null, "Background", null);
        } catch (ApiException e) {
            log.debug("GPU Job {} already deleted or not found", jobName);
        }

        try {
            coreApi.deleteNamespacedConfigMap(inputConfigMapName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.debug("Input ConfigMap already deleted or not found for GPU simulacao: {}", simulacaoId);
        }

        try {
            coreApi.deleteNamespacedConfigMap(outputConfigMapName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.debug("Output ConfigMap already deleted or not found for GPU simulacao: {}", simulacaoId);
        }

        log.info("Cleaned up GPU resources for simulacao: {}", simulacaoId);
    }
}
