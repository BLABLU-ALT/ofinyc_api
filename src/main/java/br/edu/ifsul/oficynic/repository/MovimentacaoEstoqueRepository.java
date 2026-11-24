package br.edu.ifsul.oficynic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import br.edu.ifsul.oficynic.model.MovimentacaoEstoque;

public interface MovimentacaoEstoqueRepository
        extends JpaRepository<MovimentacaoEstoque, Long> {

}