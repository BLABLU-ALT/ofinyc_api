package br.com.oficynic.ofinyc_api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Superclasse abstrata do diagrama de classes. No banco, Administrador e Funcionario
// ficam na mesma tabela (herança em tabela única) e a coluna tipo indica o perfil.
@Entity
@Table(name = "usuarios")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "tipo", length = 20)
@Getter
@Setter
@NoArgsConstructor
public abstract class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    // Sem login, o usuário só aparece como responsável e não acessa o sistema.
    @Column(unique = true, length = 60)
    private String login;

    // Guardada somente codificada (BCrypt) e nunca devolvida pela API.
    @JsonIgnore
    @Column(length = 100)
    private String senha;

    // Inativos não fazem login nem recebem novas OS, mas continuam nas OS antigas.
    @Column(nullable = false)
    private boolean ativo = true;

    // ADMINISTRADOR ou FUNCIONARIO, conforme a subclasse.
    public abstract String getPerfil();
}
