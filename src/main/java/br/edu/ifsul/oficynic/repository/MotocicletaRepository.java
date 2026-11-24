package br.edu.ifsul.oficynic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import br.edu.ifsul.oficynic.model.Motocicleta;
import jakarta.persistence.LockModeType;

public interface MotocicletaRepository
        extends JpaRepository<Motocicleta, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select moto from Motocicleta moto where moto.id = :id")
    Optional<Motocicleta> buscarComBloqueio(Long id);

    boolean existsByPlaca(String placa);

    boolean existsByPlacaAndIdNot(String placa, Long id);

    boolean existsByChassi(String chassi);

    boolean existsByChassiAndIdNot(String chassi, Long id);
}