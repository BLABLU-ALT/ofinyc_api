package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Funcionario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncionarioRepository extends JpaRepository<Funcionario, Long> { }
