package br.com.oficynic.ofinyc_api.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

// Perfil com acesso às funções administrativas: catálogo, funcionários e relatórios.
@Entity
@DiscriminatorValue("ADMINISTRADOR")
public class Administrador extends Usuario {
    @Override
    public String getPerfil() {
        return "ADMINISTRADOR";
    }
}
