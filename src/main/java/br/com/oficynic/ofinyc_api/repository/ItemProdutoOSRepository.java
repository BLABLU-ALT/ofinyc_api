package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemProdutoOSRepository extends JpaRepository<ItemProdutoOS, Long> {
    List<ItemProdutoOS> findByProdutoIdAndOrdemServicoSituacaoNotOrderById(Long produtoId, SituacaoOrdemServico situacao);
}
