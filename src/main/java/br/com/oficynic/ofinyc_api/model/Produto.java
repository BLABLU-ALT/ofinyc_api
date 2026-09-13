package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "produtos")
@Getter
@Setter
@NoArgsConstructor
public class Produto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Informe o código.")
    @Size(max = 40)
    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @NotBlank(message = "Informe a descrição.")
    @Size(max = 180)
    @Column(nullable = false, length = 180)
    private String descricao;

    @NotNull(message = "Informe o preço de venda.")
    @DecimalMin(value = "0.00", message = "O preço não pode ser negativo.")
    @Digits(integer = 12, fraction = 2)
    @Column(name = "preco_venda", nullable = false, precision = 14, scale = 2)
    private BigDecimal precoVenda;

    public void setCodigo(String valor) {
        this.codigo = valor == null ? null : valor.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void setDescricao(String valor) {
        this.descricao = valor == null ? null : valor.trim();
    }
}
