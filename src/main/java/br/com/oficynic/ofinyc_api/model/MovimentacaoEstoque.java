package br.com.oficynic.ofinyc_api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.*;

@Entity @Table(name = "movimentacoes_estoque")
@Getter @Setter @NoArgsConstructor
public class MovimentacaoEstoque {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;
    @ManyToOne(optional = false) @JoinColumn(name = "funcionario_id", nullable = false)
    private Funcionario funcionario;
    @ManyToOne @JoinColumn(name = "ordem_servico_id")
    private OrdemServico ordemServico;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10)
    private TipoMovimentacao tipo;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;
    @Column(name = "unidade_medida", nullable = false, length = 12)
    private String unidadeMedida;
    @Column(nullable = false)
    private Instant data;
    @Column(nullable = false, length = 255)
    private String motivo;
    @Column(name = "usuario_atuante", nullable = false, length = 255)
    private String usuarioAtuante;
}
