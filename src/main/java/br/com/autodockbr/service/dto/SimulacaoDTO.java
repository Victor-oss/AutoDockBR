package br.com.autodockbr.service.dto;

import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import java.io.Serializable;
import java.time.Instant;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * A DTO for the Simulacao entity.
 */
public class SimulacaoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    @NotNull
    @Size(max = 255)
    private String nome;

    private SimulacaoStatus status;

    private Long tamanhoBytes;

    private Instant dataHoraPedido;

    private Instant dataHoraConclusao;

    private Long usuarioId;

    private String usuarioNome;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public SimulacaoStatus getStatus() {
        return status;
    }

    public void setStatus(SimulacaoStatus status) {
        this.status = status;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public Instant getDataHoraPedido() {
        return dataHoraPedido;
    }

    public void setDataHoraPedido(Instant dataHoraPedido) {
        this.dataHoraPedido = dataHoraPedido;
    }

    public Instant getDataHoraConclusao() {
        return dataHoraConclusao;
    }

    public void setDataHoraConclusao(Instant dataHoraConclusao) {
        this.dataHoraConclusao = dataHoraConclusao;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getUsuarioNome() {
        return usuarioNome;
    }

    public void setUsuarioNome(String usuarioNome) {
        this.usuarioNome = usuarioNome;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SimulacaoDTO)) {
            return false;
        }
        return id != null && id.equals(((SimulacaoDTO) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "SimulacaoDTO{" +
            "id=" +
            getId() +
            ", nome='" +
            getNome() +
            "'" +
            ", status='" +
            getStatus() +
            "'" +
            ", tamanhoBytes=" +
            getTamanhoBytes() +
            ", dataHoraPedido='" +
            getDataHoraPedido() +
            "'" +
            ", dataHoraConclusao='" +
            getDataHoraConclusao() +
            "'" +
            ", usuarioId=" +
            getUsuarioId() +
            ", usuarioNome='" +
            getUsuarioNome() +
            "'" +
            "}"
        );
    }
}
