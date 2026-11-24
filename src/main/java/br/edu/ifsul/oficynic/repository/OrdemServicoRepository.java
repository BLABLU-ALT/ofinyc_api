package br.edu.ifsul.oficynic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.edu.ifsul.oficynic.model.OrdemServico;
import br.edu.ifsul.oficynic.model.SituacaoOrdemServico;
import jakarta.persistence.LockModeType;

public interface OrdemServicoRepository
        extends JpaRepository<OrdemServico, Long> {

    @Query("select distinct o from OrdemServico o join o.itens i left join fetch o.cliente "
            + "where i.servico.id = :servicoId and o.situacao in :situacoes order by o.id desc")
    List<OrdemServico> buscarPorServico(Long servicoId, List<SituacaoOrdemServico> situacoes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ordem from OrdemServico ordem where ordem.id = :id")
    Optional<OrdemServico> buscarComBloqueio(Long id);
}