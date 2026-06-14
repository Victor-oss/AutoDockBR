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
    private final KubernetesGpuJobService kubernetesGpuJobService;

    private static final int MAX_POLL_ATTEMPTS = 360; // 30 min máximo (5s * 360)
    private static final int POLL_INTERVAL_MS = 5000; // 5 segundos

    public DockingAsyncService(SimulacaoRepository simulacaoRepository, KubernetesGpuJobService kubernetesGpuJobService) {
        this.simulacaoRepository = simulacaoRepository;
        this.kubernetesGpuJobService = kubernetesGpuJobService;
    }

    @Async
    @Transactional
    public void startDockingAsync(Long simulacaoId, String workDirName, byte[] receptorBytes, byte[] liganteBytes) {
        log.info("Starting K8s docking job for simulacao: {}", simulacaoId);

        String jobName = null;
        try {
            var inicio = System.currentTimeMillis();
            jobName = kubernetesGpuJobService.submitGpuDockingJob(simulacaoId, receptorBytes, liganteBytes);

            boolean completed = waitForJobCompletion(jobName);
            var fim = System.currentTimeMillis();
            log.info("Duração do job: {} ms", fim - inicio);

            if (completed) {
                byte[] resultBytes = kubernetesGpuJobService.getJobResult(simulacaoId);

                if (resultBytes != null && resultBytes.length > 0) {
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
            if (jobName != null) {
                try {
                    kubernetesGpuJobService.cleanupJob(simulacaoId);
                } catch (Exception e) {
                    log.warn("Error cleaning up job resources for simulacao: {}", simulacaoId, e);
                }
            }
        }
    }

    private boolean waitForJobCompletion(String jobName) throws InterruptedException {
        for (int i = 0; i < MAX_POLL_ATTEMPTS; i++) {
            String status = kubernetesGpuJobService.getJobStatus(jobName);

            switch (status) {
                case "SUCCEEDED":
                    log.warn("Job {} succeeded", jobName);
                    return true;
                case "FAILED":
                case "NOT_FOUND":
                case "ERROR":
                    log.warn("Job {} failed", jobName);
                    return false;
                case "RUNNING":
                default:
                    log.warn("Job {} running", jobName);
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
