package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "motocicletas")
@Getter
@Setter
@NoArgsConstructor
public class Motocicleta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @Pattern(regexp = "[A-Za-z0-9]{7}", message = "Informe a placa com 7 letras e números, sem hífen.")
    @Column(unique = true, length = 7)
    private String placa;

    @Pattern(regexp = "[A-Za-z0-9]{17}", message = "Informe os 17 caracteres do chassi.")
    @Column(unique = true, length = 17)
    private String chassi;

    @NotBlank(message = "Informe a marca.")
    @Size(max = 80)
    @Column(nullable = false, length = 80)
    private String marca;

    @NotBlank(message = "Informe o modelo.")
    @Size(max = 100)
    @Column(nullable = false, length = 100)
    private String modelo;

    @NotNull(message = "Informe o ano.")
    @Min(1900)
    @Max(2100)
    @Column(nullable = false)
    private Integer ano;

    @Size(max = 50)
    @Column(length = 50)
    private String cor;

    @ManyToOne
    @JoinColumn(name = "cliente_id")
    @JsonIgnore
    private Cliente cliente;

    @Transient
    @Positive(message = "Selecione um cliente válido.")
    private Long clienteId;

    public Long getClienteId() {
        return cliente == null ? clienteId : cliente.getId();
    }

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public String getClienteNome() {
        return cliente == null ? null : cliente.getNome();
    }

    public void setPlaca(String valor) {
        this.placa = valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void setChassi(String valor) {
        this.chassi = valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void setMarca(String valor) {
        this.marca = valor == null ? null : valor.trim();
    }

    public void setModelo(String valor) {
        this.modelo = valor == null ? null : valor.trim();
    }

    public void setCor(String valor) {
        this.cor = valor == null ? null : valor.trim();
    }
}
