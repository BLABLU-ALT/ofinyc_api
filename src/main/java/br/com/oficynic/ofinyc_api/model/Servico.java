package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "servicos")
@Getter
@Setter
@NoArgsConstructor
public class Servico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nome;

    @Size(max = 255)
    @Column(length = 255)
    private String descricao;

    @NotNull(message = "Informe o preço.")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
    @Digits(integer = 12, fraction = 2)
    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal preco;

    public void setNome(String valor) {
        this.nome = valor == null ? null : valor.trim();
    }

    public void setDescricao(String valor) {
        this.descricao = valor == null ? null : valor.trim();
    }
}
