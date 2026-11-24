package br.edu.ifsul.oficynic.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @Size(max = 1000)
    @Column(name = "parecer_tecnico", length = 1000)
    private String parecerTecnico;

    @Column(nullable = false)
    private LocalDate dataAbertura;

    private LocalDate dataCriacao;

    private LocalDate dataAprovacao;

    private LocalDate dataAberturaAnterior;

    @ManyToOne
    @JoinColumn(name = "cliente_id", nullable = false)
    @JsonIgnore
    private Cliente cliente;

    @ManyToOne
    @JoinColumn(name = "motocicleta_id", nullable = false)
    @JsonIgnore
    private Motocicleta motocicleta;

    @ManyToOne
    @JoinColumn(name = "funcionario_id")
    @JsonIgnore
    private Usuario funcionario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoOrdemServico situacao = SituacaoOrdemServico.ORCAMENTO;

    private LocalDate dataFinalizacao;

    @ManyToOne
    @JoinColumn(name = "ordem_origem_id")
    @JsonIgnore
    private OrdemServico ordemOrigem;

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("id ASC")
    @JsonIgnore
    private List<ItemServicoOS> itens = new ArrayList<>();

    @OneToMany(mappedBy = "ordemServico", cascade = CascadeType.PERSIST, orphanRemoval = true)
    @OrderBy("id ASC")
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

        for (ItemProdutoOS item : produtos) {
            total = total.add(item.calcularSubtotal());
        }

        return total;
    }
}
