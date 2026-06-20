package br.com.autodockbr.repository;

import br.com.autodockbr.domain.Simulacao;
import br.com.autodockbr.domain.enumeration.SimulacaoStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SimulacaoRepository extends JpaRepository<Simulacao, Long> {
    @Query("SELECT s FROM Simulacao s WHERE s.usuario.id = :usuarioId")
    List<Simulacao> findAllByUsuarioId(@Param("usuarioId") Long usuarioId);

    @Query("SELECT s FROM Simulacao s WHERE s.usuario.id = :usuarioId AND s.status = :status")
    Optional<Simulacao> findByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") SimulacaoStatus status);

    @Query("SELECT COUNT(s) > 0 FROM Simulacao s WHERE s.usuario.id = :usuarioId AND s.status = :status")
    boolean existsByUsuarioIdAndStatus(@Param("usuarioId") Long usuarioId, @Param("status") SimulacaoStatus status);
}
