package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsByCodigo(String codigo);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> buscarComBloqueio(@Param("id") Long id);
    @Query(value = "SELECT COALESCE(SUM(i.quantidade),0) FROM itens_produto_os i "
            + "JOIN ordens_servico o ON o.id=i.ordem_servico_id WHERE i.produto_id=:id AND o.situacao <> 'FINALIZADA'", nativeQuery = true)
    java.math.BigDecimal quantidadeEmpenhada(@Param("id") Long id);
}
