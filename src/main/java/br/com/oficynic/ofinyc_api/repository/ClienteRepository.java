package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByCpfAndIdNot(String cpf, Long id);
    boolean existsByCpf(String cpf);
}
