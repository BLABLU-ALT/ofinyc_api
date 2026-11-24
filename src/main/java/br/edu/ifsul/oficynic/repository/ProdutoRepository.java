package br.edu.ifsul.oficynic.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.ifsul.oficynic.model.Produto;
import jakarta.persistence.LockModeType;

public interface ProdutoRepository
        extends JpaRepository<Produto, Long> {

    boolean existsByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Produto p where p.id = :id")
    Optional<Produto> buscarComBloqueio(@Param("id") Long id);

    @Query(value = "SELECT COALESCE(SUM(i.quantidade),0) FROM itens_produto_os i "
            + "JOIN ordens_servico o ON o.id=i.ordem_servico_id WHERE i.produto_id=:id "
            + "AND o.situacao IN ('ORCAMENTO','EM_ANDAMENTO','RETRABALHO')", nativeQuery = true)
    BigDecimal quantidadeEmpenhada(@Param("id") Long id);
}