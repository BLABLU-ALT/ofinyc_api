package br.edu.ifsul.oficynic.model;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

@Entity
@DiscriminatorValue("FUNCIONARIO")
public class Funcionario extends Usuario {

    @Override
    public String getPerfil() {
        return "FUNCIONARIO";
    }
}
