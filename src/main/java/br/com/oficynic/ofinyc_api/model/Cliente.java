package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.br.CPF;

@Entity
@Table(name = "clientes")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long id;

    @NotBlank(message = "Informe o nome.")
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String nome;

    @NotBlank(message = "Informe o CPF.")
    @Pattern(regexp = "[0-9]{11}", message = "Informe os 11 números do CPF.")
    // Confere os dois dígitos verificadores e rejeita números repetidos.
    @CPF(message = "CPF inválido. Confira os números informados.")
    @Column(nullable = false, unique = true, length = 11)
    private String cpf;

    @Size(max = 20)
    @Column(length = 20)
    private String telefone;

    @Email(message = "Informe um e-mail válido.")
    @Size(max = 160)
    @Column(length = 160)
    private String email;

    @Size(max = 255)
    @Column(length = 255)
    private String endereco;

    @OneToMany(mappedBy = "cliente")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<OrdemServico> ordensServico = new ArrayList<>();

    public void setNome(String valor) {
        this.nome = valor == null ? null : valor.trim();
    }

    public void setCpf(String valor) {
        this.cpf = valor == null ? null : valor.trim();
    }

    public void setTelefone(String valor) {
        this.telefone = valor == null ? null : valor.trim();
    }

    public void setEmail(String valor) {
        this.email = valor == null ? null : valor.trim();
    }

    public void setEndereco(String valor) {
        this.endereco = valor == null ? null : valor.trim();
    }
}
