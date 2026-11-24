package br.edu.ifsul.oficynic.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("ADMINISTRADOR")
public class Administrador extends Usuario {

    @Override
    public String getPerfil() {
        return "ADMINISTRADOR";
    }
}
