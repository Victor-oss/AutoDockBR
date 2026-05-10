package br.com.autodockbr.web.rest;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.service.SimulacaoEmProgressoException;
import br.com.autodockbr.service.SimulacaoService;
import br.com.autodockbr.service.dto.SimulacaoDTO;
import br.com.autodockbr.web.rest.errors.BadRequestAlertException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tech.jhipster.web.util.HeaderUtil;
import tech.jhipster.web.util.ResponseUtil;

@RestController
@RequestMapping("/api")
public class SimulacaoResource {

    private final Logger log = LoggerFactory.getLogger(SimulacaoResource.class);

    private static final String ENTITY_NAME = "simulacao";

    private final SimulacaoService simulacaoService;

    public SimulacaoResource(SimulacaoService simulacaoService) {
        this.simulacaoService = simulacaoService;
    }

    @PostMapping(value = "/simulacoes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SimulacaoDTO> createSimulacao(
        @RequestParam("nome") @NotNull String nome,
        @RequestParam("receptor") @NotNull MultipartFile receptor,
        @RequestParam("ligante") @NotNull MultipartFile ligante
    ) throws URISyntaxException {
        log.debug("REST request to create Simulacao : {}", nome);

        if (!isValidPdbFile(receptor)) {
            throw new BadRequestAlertException("O arquivo do receptor deve ser um arquivo .pdb", ENTITY_NAME, "invalidreceptor");
        }
        if (!isValidPdbFile(ligante)) {
            throw new BadRequestAlertException("O arquivo do ligante deve ser um arquivo .pdb", ENTITY_NAME, "invalidligante");
        }

        try {
            SimulacaoDTO result = simulacaoService.createAndStartDocking(nome, receptor, ligante);
            return ResponseEntity
                .created(new URI("/api/simulacoes/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert("AutoDockBR", true, ENTITY_NAME, result.getId().toString()))
                .body(result);
        } catch (SimulacaoEmProgressoException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "simulacaoemandamento");
        }
    }

    @GetMapping("/simulacoes")
    public List<SimulacaoDTO> getAllSimulacoes() {
        log.debug("REST request to get all Simulacoes for current user");
        return simulacaoService.findAllByCurrentUser();
    }

    @GetMapping("/simulacoes/{id}")
    public ResponseEntity<SimulacaoDTO> getSimulacao(@PathVariable Long id) {
        log.debug("REST request to get Simulacao : {}", id);
        Optional<SimulacaoDTO> simulacaoDTO = simulacaoService.findOne(id);
        return ResponseUtil.wrapOrNotFound(simulacaoDTO);
    }

    @GetMapping("/simulacoes/{id}/download")
    public ResponseEntity<byte[]> downloadResultado(@PathVariable Long id) {
        log.debug("REST request to download Simulacao result : {}", id);

        Optional<Simulacao> optSimulacao = simulacaoService.findOneWithResultado(id);

        if (optSimulacao.isEmpty() || optSimulacao.get().getResultado() == null) {
            return ResponseEntity.notFound().build();
        }

        Simulacao simulacao = optSimulacao.get();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", simulacao.getNome() + "_resultado.dlg");
        headers.setContentLength(simulacao.getResultado().length);

        return new ResponseEntity<>(simulacao.getResultado(), headers, HttpStatus.OK);
    }

    private boolean isValidPdbFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        String originalFilename = file.getOriginalFilename();
        return originalFilename != null && originalFilename.toLowerCase().endsWith(".pdb");
    }
}
