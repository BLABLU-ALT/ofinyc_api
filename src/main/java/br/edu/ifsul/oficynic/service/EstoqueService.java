package br.edu.ifsul.oficynic.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.edu.ifsul.oficynic.model.MovimentacaoEstoque;
import br.edu.ifsul.oficynic.model.OrdemServico;
import br.edu.ifsul.oficynic.model.Produto;
import br.edu.ifsul.oficynic.model.TipoMovimentacao;
import br.edu.ifsul.oficynic.model.Usuario;
import br.edu.ifsul.oficynic.repository.MovimentacaoEstoqueRepository;
import br.edu.ifsul.oficynic.repository.ProdutoRepository;
import br.edu.ifsul.oficynic.repository.UsuarioRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@Service
@Transactional
public class EstoqueService {

    private static final BigDecimal ESTOQUE_MAXIMO = new BigDecimal("999999999999.999");

    private final ProdutoRepository produtoRepository;
    private final UsuarioRepository usuarioRepository;
    private final MovimentacaoEstoqueRepository movimentacaoEstoqueRepository;
    private final EntityManager entityManager;

    public EstoqueService(
            ProdutoRepository produtoRepository,
            UsuarioRepository usuarioRepository,
            MovimentacaoEstoqueRepository movimentacaoEstoqueRepository,
            EntityManager entityManager) {

        this.produtoRepository = produtoRepository;
        this.usuarioRepository = usuarioRepository;
        this.movimentacaoEstoqueRepository = movimentacaoEstoqueRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<MovimentacaoEstoque> listar() {
        return movimentacaoEstoqueRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public MovimentacaoEstoque movimentar(
            Long produtoId,
            Long funcionarioId,
            TipoMovimentacao tipo,
            BigDecimal quantidade,
            BigDecimal valorUnitario,
            String motivo,
            String usuario) {

        Produto produto = bloquearProduto(produtoId);

        Usuario funcionario = usuarioRepository.findById(funcionarioId)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Funcionário não encontrado."));

        if (!funcionario.isAtivo()) {
            throw erro(HttpStatus.CONFLICT, "Funcionário inativo não pode registrar movimentações.");
        }

        if (tipo == TipoMovimentacao.ENTRADA && valorUnitario == null) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe o valor unitário de compra.");
        }

        MovimentacaoEstoque movimento = registrar(produto, funcionario, null, tipo, quantidade, motivo.trim(), usuario);

        // Entrada atualiza o preco de custo e as OS antigas mantem o custo da epoca
        if (tipo == TipoMovimentacao.ENTRADA) {
            movimento.setValorUnitario(valorUnitario);
            produto.setPrecoCusto(valorUnitario);
        }

        produtoRepository.flush();

        return movimento;
    }

    public Produto bloquearProduto(Long id) {
        Produto produto = produtoRepository.buscarComBloqueio(id)
                .orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Produto não encontrado."));

        // Rele o estoque apos o bloqueio porque outra transacao pode ter alterado
        entityManager.refresh(produto, LockModeType.PESSIMISTIC_WRITE);
        produto.setEmpenho(produtoRepository.quantidadeEmpenhada(id));

        return produto;
    }

    // Quem chama ja bloqueou o produto na mesma transacao
    public MovimentacaoEstoque registrar(
            Produto produto,
            Usuario funcionario,
            OrdemServico ordem,
            TipoMovimentacao tipo,
            BigDecimal quantidade,
            String motivo,
            String usuario) {

        if (produto.getUnidadeMedida() == null || produto.getUnidadeMedida().isBlank()) {
            throw erro(HttpStatus.CONFLICT, "Complete a unidade de medida no cadastro do produto.");
        }

        boolean quantidadeInvalida = quantidade == null
                || quantidade.signum() <= 0
                || quantidade.scale() > 3
                || quantidade.precision() - quantidade.scale() > 12;

        if (quantidadeInvalida) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe quantidade positiva com até três casas decimais.");
        }

        if (tipo == TipoMovimentacao.SAIDA && ordem == null && quantidade.compareTo(produto.getSaldo()) > 0) {
            throw erro(HttpStatus.CONFLICT, "Saldo disponível insuficiente; há produtos empenhados em OS.");
        }

        BigDecimal fisico = tipo == TipoMovimentacao.ENTRADA
                ? produto.getEstoque().add(quantidade)
                : produto.getEstoque().subtract(quantidade);

        if (fisico.signum() < 0) {
            throw erro(HttpStatus.CONFLICT, "Estoque insuficiente para esta saída.");
        }

        if (fisico.compareTo(ESTOQUE_MAXIMO) > 0) {
            throw erro(HttpStatus.CONFLICT, "O estoque ultrapassa o limite permitido.");
        }

        produto.setEstoque(fisico);

        MovimentacaoEstoque movimento = new MovimentacaoEstoque();
        movimento.setProduto(produto);
        movimento.setFuncionario(funcionario);
        movimento.setOrdemServico(ordem);
        movimento.setTipo(tipo);
        movimento.setQuantidade(quantidade);
        movimento.setUnidadeMedida(produto.getUnidadeMedida());
        movimento.setData(Instant.now());
        movimento.setMotivo(motivo);
        movimento.setUsuarioAtuante(usuario);

        return movimentacaoEstoqueRepository.saveAndFlush(movimento);
    }

    private ResponseStatusException erro(HttpStatus status, String mensagem) {
        return new ResponseStatusException(status, mensagem);
    }
}
