package br.com.oficynic.ofinyc_api.domain;

import java.util.Objects;

/**
 * Guarda os dados básicos da moto. Ainda não salva no banco.
 */
public class Motocicleta {

    private final Long id;
    private final String placa;

    // Recebe o ID pronto; não gera nem busca esse ID no banco.
    public Motocicleta(Long id, String placa) {
        this.id = Objects.requireNonNull(id, "O identificador e obrigatorio.");
        this.placa = placa;
    }

    public Long getId() {
        return id;
    }

    public String getPlaca() {
        return placa;
    }
}
