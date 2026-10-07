package br.com.oficynic.ofinyc_api.repository;

import br.com.oficynic.ofinyc_api.model.Motocicleta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface MotocicletaRepository extends JpaRepository<Motocicleta, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select moto from Motocicleta moto where moto.id = :id")
    Optional<Motocicleta> buscarComBloqueio(Long id);
    boolean existsByPlacaAndIdNot(String placa, Long id);
    boolean existsByPlaca(String placa);
    boolean existsByChassiAndIdNot(String chassi, Long id);
    boolean existsByChassi(String chassi);
}
