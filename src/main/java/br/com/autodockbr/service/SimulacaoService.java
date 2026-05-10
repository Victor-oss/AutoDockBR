package br.com.autodockbr.service;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.domain.User;
import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import br.com.autodockbr.repository.SimulacaoRepository;
import br.com.autodockbr.repository.UserRepository;
import br.com.autodockbr.security.SecurityUtils;
import br.com.autodockbr.service.dto.SimulacaoDTO;
import br.com.autodockbr.service.mapper.SimulacaoMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class SimulacaoService {

    private final Logger log = LoggerFactory.getLogger(SimulacaoService.class);

    private final SimulacaoRepository simulacaoRepository;
    private final UserRepository userRepository;
    private final SimulacaoMapper simulacaoMapper;
    private final DockingAsyncService dockingAsyncService;

    public SimulacaoService(
        SimulacaoRepository simulacaoRepository,
        UserRepository userRepository,
        SimulacaoMapper simulacaoMapper,
        DockingAsyncService dockingAsyncService
    ) {
        this.simulacaoRepository = simulacaoRepository;
        this.userRepository = userRepository;
        this.simulacaoMapper = simulacaoMapper;
        this.dockingAsyncService = dockingAsyncService;
    }

    @Transactional(readOnly = true)
    public List<SimulacaoDTO> findAllByCurrentUser() {
        log.debug("Request to get all Simulacoes for current user");
        String email = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("Current user not found"));
        User user = userRepository.findOneByEmailIgnoreCase(email).orElseThrow(() -> new RuntimeException("User not found"));
        return simulacaoMapper.toDto(simulacaoRepository.findAllByUsuarioId(user.getId()));
    }

    @Transactional(readOnly = true)
    public Optional<SimulacaoDTO> findOne(Long id) {
        log.debug("Request to get Simulacao : {}", id);
        return simulacaoRepository.findById(id).map(simulacaoMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<Simulacao> findOneWithResultado(Long id) {
        log.debug("Request to get Simulacao result : {}", id);
        return simulacaoRepository.findById(id);
    }

    public SimulacaoDTO createAndStartDocking(String nome, MultipartFile receptorFile, MultipartFile liganteFile) {
        log.debug("Request to create Simulacao : {}", nome);

        String email = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new RuntimeException("Current user not found"));
        User user = userRepository.findOneByEmailIgnoreCase(email).orElseThrow(() -> new RuntimeException("User not found"));

        if (simulacaoRepository.existsByUsuarioIdAndStatus(user.getId(), SimulacaoStatus.EM_PROGRESSO)) {
            throw new SimulacaoEmProgressoException();
        }

        Simulacao simulacao = new Simulacao();
        simulacao.setNome(nome);
        simulacao.setStatus(SimulacaoStatus.EM_PROGRESSO);
        simulacao.setDataHoraPedido(Instant.now());
        simulacao.setUsuario(user);

        simulacao = simulacaoRepository.save(simulacao);

        String workDirName = "sim_" + simulacao.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            byte[] receptorBytes = receptorFile.getBytes();
            byte[] liganteBytes = liganteFile.getBytes();
            dockingAsyncService.startDockingAsync(simulacao.getId(), workDirName, receptorBytes, liganteBytes);
        } catch (IOException e) {
            log.error("Error reading uploaded files", e);
            throw new RuntimeException("Erro ao ler os arquivos enviados", e);
        }

        return simulacaoMapper.toDto(simulacao);
    }
}
