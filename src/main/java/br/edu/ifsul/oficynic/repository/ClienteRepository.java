package br.edu.ifsul.oficynic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.edu.ifsul.oficynic.model.Cliente;

public interface ClienteRepository
        extends JpaRepository<Cliente, Long> {

    boolean existsByCpf(String cpf);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    // Compara só os números, para "(51) 99999-0000" e "51999990000" serem o mesmo telefone.
    @Query(value = "SELECT EXISTS (SELECT 1 FROM clientes WHERE "
            + "regexp_replace(telefone, '[^0-9]', '', 'g') = :telefone "
            + "AND (CAST(:id AS bigint) IS NULL OR id <> :id))", nativeQuery = true)
    boolean existeOutroTelefone(@Param("telefone") String telefone, @Param("id") Long id);
}