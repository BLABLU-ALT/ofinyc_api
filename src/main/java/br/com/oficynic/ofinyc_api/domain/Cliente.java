package br.com.oficynic.ofinyc_api.domain;

import java.util.Objects;

/**
 * Guarda os dados básicos do cliente. Ainda não salva no banco.
 */
public class Cliente {

    private final Long id;
    private final String nome;

    // Recebe o ID pronto; não gera nem busca esse ID no banco.
    public Cliente(Long id, String nome) {
        this.id = Objects.requireNonNull(id, "O identificador e obrigatorio.");
        this.nome = nome;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }
}
