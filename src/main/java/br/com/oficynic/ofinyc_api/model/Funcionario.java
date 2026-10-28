package br.com.oficynic.ofinyc_api.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// Perfil operacional: abre e acompanha OS, registra parecer técnico e itens.
@Entity
@DiscriminatorValue("FUNCIONARIO")
public class Funcionario extends Usuario {
    @Override
    public String getPerfil() {
        return "FUNCIONARIO";
    }
}
