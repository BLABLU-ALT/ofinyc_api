package br.edu.ifsul.oficynic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifsul.oficynic.model.ItemProdutoOS;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;

public interface ItemProdutoOSRepository
        extends JpaRepository<ItemProdutoOS, Long> {

    List<ItemProdutoOS> findByProdutoIdAndOrdemServicoSituacaoInOrderById(
            Long produtoId,
            Collection<SituacaoOrdemServico> situacoes);
}