package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsByCodigo(String codigo);
}
