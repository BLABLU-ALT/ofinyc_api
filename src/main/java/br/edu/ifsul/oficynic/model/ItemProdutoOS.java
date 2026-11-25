package br.edu.ifsul.oficynic.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "itens_produto_os")
@Getter
@Setter
@NoArgsConstructor
public class ItemProdutoOS {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "ordem_servico_id", nullable = false)
    private OrdemServico ordemServico;

    @ManyToOne(optional = false)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 14, scale = 2)
    private BigDecimal valorUnitario;

    @Column(name = "unidade_medida", nullable = false, length = 12)
    private String unidadeMedida;

    // Custo do produto na inclusao ou nulo se nao havia custo
    @Column(name = "custo_unitario", precision = 14, scale = 2)
    private BigDecimal custoUnitario;

    public BigDecimal calcularSubtotal() {
        return quantidade.multiply(valorUnitario).setScale(2, RoundingMode.HALF_UP);
    }
}
