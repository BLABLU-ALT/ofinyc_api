package br.com.oficynic.ofinyc_api.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Junta os dados da OS. Criar esse objeto não salva no banco nem abre uma OS pela API.
 */
public class OrdemServico {

    // final impede trocar o valor destes campos depois que o objeto é criado.
    private final Cliente cliente;
    private final Motocicleta motocicleta;
    private final Funcionario funcionarioResponsavel;
    // Instant representa data e hora sem depender do fuso do computador.
    private final Instant dataAbertura;
    private final SituacaoOrdemServico situacao;

    public OrdemServico(Cliente cliente, Motocicleta motocicleta,
                        Funcionario funcionarioResponsavel, Instant dataAbertura) {
        // RN05: não deixa criar o objeto da OS sem cliente, moto e responsável.
        this.cliente = Objects.requireNonNull(cliente, "O cliente e obrigatorio.");
        this.motocicleta = Objects.requireNonNull(motocicleta, "A motocicleta e obrigatoria.");
        this.funcionarioResponsavel = Objects.requireNonNull(
                funcionarioResponsavel, "O funcionario responsavel e obrigatorio.");
        this.dataAbertura = Objects.requireNonNull(dataAbertura, "A data de abertura e obrigatoria.");
        this.situacao = SituacaoOrdemServico.ABERTA;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public Motocicleta getMotocicleta() {
        return motocicleta;
    }

    public Funcionario getFuncionarioResponsavel() {
        return funcionarioResponsavel;
    }

    public Instant getDataAbertura() {
        return dataAbertura;
    }

    public SituacaoOrdemServico getSituacao() {
        return situacao;
    }
}
