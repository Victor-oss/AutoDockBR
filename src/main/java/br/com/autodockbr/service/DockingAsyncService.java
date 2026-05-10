package br.com.autodockbr.service;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import br.com.autodockbr.repository.SimulacaoRepository;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private static final String DOCKING_DATA_PATH = System.getProperty("user.home") + "/.AutoDockBR/docking-data";
    private static final String AUTODOCK_CONTAINER = "autodock";

    public DockingAsyncService(SimulacaoRepository simulacaoRepository) {
        this.simulacaoRepository = simulacaoRepository;
    }

    @Async
    @Transactional
    public void startDockingAsync(Long simulacaoId, String workDirName, byte[] receptorBytes, byte[] liganteBytes) {
        log.info("Starting async docking process for simulacao: {}", simulacaoId);

        try {
            Path workDir = Paths.get(DOCKING_DATA_PATH, workDirName);
            Files.createDirectories(workDir);

            String receptorFileName = "receptor.pdb";
            String liganteFileName = "ligand.pdb";

            Path receptorPath = workDir.resolve(receptorFileName);
            Path ligantePath = workDir.resolve(liganteFileName);

            Files.write(receptorPath, receptorBytes);
            Files.write(ligantePath, liganteBytes);

            String containerWorkDir = "/data/" + workDirName;

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_receptor4.py -r %s -o receptor.pdbqt'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir,
                    receptorFileName
                )
            );

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_ligand4.py -l %s -o ligand.pdbqt'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir,
                    liganteFileName
                )
            );

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_gpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.gpf'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir
                )
            );

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && autogrid4 -p config.gpf -l autogrid.glg'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir
                )
            );

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && python2 /opt/mgltools/MGLToolsPckgs/AutoDockTools/Utilities24/prepare_dpf4.py -r receptor.pdbqt -l ligand.pdbqt -o config.dpf'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir
                )
            );

            executeDockerCommand(
                String.format(
                    "docker exec %s sh -c 'export PYTHONPATH=/opt/mgltools/MGLToolsPckgs && cd %s && autodock4 -p config.dpf -l resultado.dlg'",
                    AUTODOCK_CONTAINER,
                    containerWorkDir
                )
            );

            Path resultPath = workDir.resolve("resultado.dlg");
            byte[] resultBytes = Files.readAllBytes(resultPath);

            Optional<Simulacao> optSimulacao = simulacaoRepository.findById(simulacaoId);
            if (optSimulacao.isPresent()) {
                Simulacao simulacao = optSimulacao.get();
                simulacao.setResultado(resultBytes);
                simulacao.setResultadoContentType("text/plain");
                simulacao.setTamanhoBytes((long) resultBytes.length);
                simulacao.setStatus(SimulacaoStatus.CONCLUIDO);
                simulacao.setDataHoraConclusao(Instant.now());
                simulacaoRepository.save(simulacao);
                log.info("Docking completed successfully for simulacao: {}", simulacaoId);
            }
        } catch (Exception e) {
            log.error("Error during docking process for simulacao: {}", simulacaoId, e);

            Optional<Simulacao> optSimulacao = simulacaoRepository.findById(simulacaoId);
            if (optSimulacao.isPresent()) {
                Simulacao simulacao = optSimulacao.get();
                simulacao.setStatus(SimulacaoStatus.ERRO);
                simulacao.setDataHoraConclusao(Instant.now());
                simulacaoRepository.save(simulacao);
            }
        }
    }

    private void executeDockerCommand(String command) throws Exception {
        log.debug("Executing Docker command: {}", command);

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command("sh", "-c", command);
        processBuilder.redirectErrorStream(true);

        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                log.debug("Docker output: {}", line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Docker command failed with exit code " + exitCode + ": " + output.toString());
        }
    }

    @SuppressWarnings("unused")
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
