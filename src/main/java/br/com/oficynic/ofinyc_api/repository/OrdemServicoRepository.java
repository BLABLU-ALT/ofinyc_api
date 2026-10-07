package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.OrdemServico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface OrdemServicoRepository extends JpaRepository<OrdemServico, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ordem from OrdemServico ordem where ordem.id = :id")
    Optional<OrdemServico> buscarComBloqueio(Long id);
}
