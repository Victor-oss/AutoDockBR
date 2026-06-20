package br.com.autodockbr.domain;

import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.time.Instant;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "simulacao")
public class Simulacao implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sequenceGenerator")
    @SequenceGenerator(name = "sequenceGenerator")
    @Column(name = "id")
    private Long id;

    @NotNull
    @Size(max = 255)
    @Column(name = "nome", length = 255, nullable = false)
    private String nome;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50, nullable = false)
    private SimulacaoStatus status;

    @Lob
    @Column(name = "resultado")
    private byte[] resultado;

    @Column(name = "resultado_content_type")
    private String resultadoContentType;

    @Column(name = "tamanho_bytes")
    private Long tamanhoBytes;

    @NotNull
    @Column(name = "data_hora_pedido", nullable = false)
    private Instant dataHoraPedido;

    @Column(name = "data_hora_conclusao")
    private Instant dataHoraConclusao;

    @ManyToOne(optional = false)
    @NotNull
    @JsonIgnoreProperties(value = { "simulacoes" }, allowSetters = true)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Simulacao id(Long id) {
        this.setId(id);
        return this;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Simulacao nome(String nome) {
        this.setNome(nome);
        return this;
    }

    public SimulacaoStatus getStatus() {
        return status;
    }

    public void setStatus(SimulacaoStatus status) {
        this.status = status;
    }

    public Simulacao status(SimulacaoStatus status) {
        this.setStatus(status);
        return this;
    }

    public byte[] getResultado() {
        return resultado;
    }

    public void setResultado(byte[] resultado) {
        this.resultado = resultado;
    }

    public Simulacao resultado(byte[] resultado) {
        this.setResultado(resultado);
        return this;
    }

    public String getResultadoContentType() {
        return resultadoContentType;
    }

    public void setResultadoContentType(String resultadoContentType) {
        this.resultadoContentType = resultadoContentType;
    }

    public Simulacao resultadoContentType(String resultadoContentType) {
        this.setResultadoContentType(resultadoContentType);
        return this;
    }

    public Long getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(Long tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public Simulacao tamanhoBytes(Long tamanhoBytes) {
        this.setTamanhoBytes(tamanhoBytes);
        return this;
    }

    public Instant getDataHoraPedido() {
        return dataHoraPedido;
    }

    public void setDataHoraPedido(Instant dataHoraPedido) {
        this.dataHoraPedido = dataHoraPedido;
    }

    public Simulacao dataHoraPedido(Instant dataHoraPedido) {
        this.setDataHoraPedido(dataHoraPedido);
        return this;
    }

    public Instant getDataHoraConclusao() {
        return dataHoraConclusao;
    }

    public void setDataHoraConclusao(Instant dataHoraConclusao) {
        this.dataHoraConclusao = dataHoraConclusao;
    }

    public Simulacao dataHoraConclusao(Instant dataHoraConclusao) {
        this.setDataHoraConclusao(dataHoraConclusao);
        return this;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public Simulacao usuario(User usuario) {
        this.setUsuario(usuario);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Simulacao)) {
            return false;
        }
        return id != null && id.equals(((Simulacao) o).id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "Simulacao{" +
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
            "}"
        );
    }
}
