-- Registros das aulas anteriores podem continuar sem motocicleta.
ALTER TABLE ordens_servico
    ADD COLUMN motocicleta_id BIGINT REFERENCES motocicletas(id);

CREATE INDEX idx_ordens_servico_motocicleta ON ordens_servico(motocicleta_id);
