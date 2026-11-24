package br.edu.ifsul.oficynic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifsul.oficynic.model.Servico;

public interface ServicoRepository
        extends JpaRepository<Servico, Long> {

}