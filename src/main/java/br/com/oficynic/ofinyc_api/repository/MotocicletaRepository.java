package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Motocicleta;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MotocicletaRepository extends JpaRepository<Motocicleta, Long> {
    boolean existsByPlacaAndIdNot(String placa, Long id);
    boolean existsByPlaca(String placa);
    boolean existsByChassiAndIdNot(String chassi, Long id);
    boolean existsByChassi(String chassi);
}
