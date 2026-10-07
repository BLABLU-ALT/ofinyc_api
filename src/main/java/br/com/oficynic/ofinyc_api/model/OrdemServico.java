package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A OS reúne os vínculos, serviços e situação do atendimento.
@Entity
@Table(name = "ordens_servico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrdemServico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Informe a descrição.")
    @Size(max = 255)
    @Column(nullable = false, length = 255)
    private String descricao;

    @NotNull(message = "Informe a data de abertura.")
    @Column(nullable = false)
    private LocalDate dataAbertura;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    @JsonIgnore
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "motocicleta_id")
    @JsonIgnore
    private Motocicleta motocicleta;

    @ManyToOne
    @JoinColumn(name = "funcionario_id")
    @JsonIgnore
    private Funcionario funcionario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoOrdemServico situacao = SituacaoOrdemServico.ABERTA;

    private LocalDate dataFinalizacao;

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @jakarta.persistence.OrderBy("id ASC")
    @JsonIgnore
    private List<ItemServicoOS> itens = new ArrayList<>();

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @jakarta.persistence.OrderBy("id ASC")
    @JsonIgnore
    private List<ItemProdutoOS> produtos = new ArrayList<>();

    public BigDecimal calcularValorTotal() {
        return calcularTotalServicos().add(calcularTotalProdutos());
    }

    public BigDecimal calcularTotalServicos() {
        BigDecimal total = new BigDecimal("0.00");
        for (ItemServicoOS item : itens) {
            total = total.add(item.calcularSubtotal());
        }
        return total;
    }

    public BigDecimal calcularTotalProdutos() {
        BigDecimal total = new BigDecimal("0.00");
        for (ItemProdutoOS item : produtos) total = total.add(item.calcularSubtotal());
        return total;
    }
}
