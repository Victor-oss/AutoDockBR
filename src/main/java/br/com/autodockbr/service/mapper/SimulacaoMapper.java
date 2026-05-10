package br.com.autodockbr.service.mapper;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.service.dto.SimulacaoDTO;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SimulacaoMapper {

    public List<SimulacaoDTO> toDto(List<Simulacao> simulacoes) {
        return simulacoes.stream().filter(Objects::nonNull).map(this::toDto).collect(Collectors.toList());
    }

    public SimulacaoDTO toDto(Simulacao simulacao) {
        if (simulacao == null) {
            return null;
        }
        SimulacaoDTO dto = new SimulacaoDTO();
        dto.setId(simulacao.getId());
        dto.setNome(simulacao.getNome());
        dto.setStatus(simulacao.getStatus());
        dto.setTamanhoBytes(simulacao.getTamanhoBytes());
        dto.setDataHoraPedido(simulacao.getDataHoraPedido());
        dto.setDataHoraConclusao(simulacao.getDataHoraConclusao());
        if (simulacao.getUsuario() != null) {
            dto.setUsuarioId(simulacao.getUsuario().getId());
            dto.setUsuarioNome(simulacao.getUsuario().getName());
        }
        return dto;
    }

    public Simulacao toEntity(SimulacaoDTO dto) {
        if (dto == null) {
            return null;
        }
        Simulacao simulacao = new Simulacao();
        simulacao.setId(dto.getId());
        simulacao.setNome(dto.getNome());
        simulacao.setStatus(dto.getStatus());
        simulacao.setTamanhoBytes(dto.getTamanhoBytes());
        simulacao.setDataHoraPedido(dto.getDataHoraPedido());
        simulacao.setDataHoraConclusao(dto.getDataHoraConclusao());
        return simulacao;
    }

    public void partialUpdate(Simulacao entity, SimulacaoDTO dto) {
        if (dto.getNome() != null) {
            entity.setNome(dto.getNome());
        }
        if (dto.getStatus() != null) {
            entity.setStatus(dto.getStatus());
        }
        if (dto.getTamanhoBytes() != null) {
            entity.setTamanhoBytes(dto.getTamanhoBytes());
        }
        if (dto.getDataHoraPedido() != null) {
            entity.setDataHoraPedido(dto.getDataHoraPedido());
        }
        if (dto.getDataHoraConclusao() != null) {
            entity.setDataHoraConclusao(dto.getDataHoraConclusao());
        }
    }
}
