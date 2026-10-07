package br.com.oficynic.ofinyc_api.service;

import br.com.oficynic.ofinyc_api.model.*;
import br.com.oficynic.ofinyc_api.repository.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service @Transactional
public class EstoqueService {
    private final ProdutoRepository produtos;
    private final FuncionarioRepository funcionarios;
    private final MovimentacaoEstoqueRepository movimentos;
    private final EntityManager entityManager;
    public EstoqueService(ProdutoRepository produtos, FuncionarioRepository funcionarios, MovimentacaoEstoqueRepository movimentos, EntityManager entityManager) {
        this.produtos = produtos; this.funcionarios = funcionarios; this.movimentos = movimentos;
        this.entityManager = entityManager;
    }
    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> listar() { return movimentos.findAll(Sort.by(Sort.Direction.DESC, "id")); }
    public MovimentacaoEstoque movimentar(Long produtoId, Long funcionarioId, TipoMovimentacao tipo,
                                         BigDecimal quantidade, String motivo, String usuario) {
        var produto = bloquearProduto(produtoId);
        var funcionario = funcionarios.findById(funcionarioId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));
        var movimento = registrar(produto, funcionario, null, tipo, quantidade, motivo.trim(), usuario);
        produtos.flush();
        return movimento;
    }
    public Produto bloquearProduto(Long id) {
        var produto = produtos.buscarComBloqueio(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Produto não encontrado."));
        // Recarrega o físico após esperar o lock, mesmo se a entidade já estava no contexto da OS.
        entityManager.refresh(produto, LockModeType.PESSIMISTIC_WRITE);
        produto.setEmpenho(produtos.quantidadeEmpenhada(id));
        return produto;
    }
    // O chamador mantém o bloqueio do produto e a mesma transação da OS.
    public MovimentacaoEstoque registrar(Produto produto, Funcionario funcionario, OrdemServico ordem,
                                        TipoMovimentacao tipo, BigDecimal quantidade, String motivo, String usuario) {
        if (produto.getUnidadeMedida() == null || produto.getUnidadeMedida().isBlank())
            throw erro(HttpStatus.CONFLICT, "Complete a unidade de medida no cadastro do produto.");
        if (quantidade == null || quantidade.signum() <= 0 || quantidade.scale() > 3 || quantidade.precision() - quantidade.scale() > 12)
            throw erro(HttpStatus.BAD_REQUEST, "Informe quantidade positiva com até três casas decimais.");
        if (tipo == TipoMovimentacao.SAIDA && ordem == null && quantidade.compareTo(produto.getSaldo()) > 0)
            throw erro(HttpStatus.CONFLICT, "Saldo disponível insuficiente; há produtos empenhados em OS.");
        var fisico = tipo == TipoMovimentacao.ENTRADA ? produto.getEstoque().add(quantidade) : produto.getEstoque().subtract(quantidade);
        if (fisico.signum() < 0) throw erro(HttpStatus.CONFLICT, "Estoque insuficiente para esta saída.");
        if (fisico.compareTo(new BigDecimal("999999999999.999")) > 0) throw erro(HttpStatus.CONFLICT, "O estoque ultrapassa o limite permitido.");
        produto.setEstoque(fisico);
        var movimento = new MovimentacaoEstoque();
        movimento.setProduto(produto); movimento.setFuncionario(funcionario); movimento.setOrdemServico(ordem);
        movimento.setTipo(tipo); movimento.setQuantidade(quantidade); movimento.setUnidadeMedida(produto.getUnidadeMedida());
        movimento.setData(Instant.now()); movimento.setMotivo(motivo); movimento.setUsuarioAtuante(usuario);
        return movimentos.saveAndFlush(movimento);
    }
    private ResponseStatusException erro(HttpStatus status, String mensagem) { return new ResponseStatusException(status, mensagem); }
}
