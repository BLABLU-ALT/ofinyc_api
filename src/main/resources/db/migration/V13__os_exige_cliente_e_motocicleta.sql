-- A ordem de serviço passa a exigir cliente e motocicleta também no banco.
-- As colunas nasceram opcionais (V2 e V3) porque já havia ordens gravadas sem esses vínculos.
-- Se ainda existir alguma ordem assim, a migração para com uma mensagem e nada é alterado:
-- os vínculos não são inventados nem as ordens apagadas automaticamente.
-- O funcionário responsável continua opcional no banco: o retrabalho de uma ordem cujo
-- responsável foi desativado nasce sem responsável, e o sistema exige escolher um antes de continuar.
DO $$
DECLARE
    sem_vinculo INTEGER;
BEGIN
    SELECT count(*) INTO sem_vinculo
      FROM ordens_servico
     WHERE cliente_id IS NULL OR motocicleta_id IS NULL;
    IF sem_vinculo > 0 THEN
        RAISE EXCEPTION 'Existem % ordens de serviço sem cliente ou motocicleta. Complete esses vínculos antes de aplicar a V13.', sem_vinculo;
    END IF;
END $$;

ALTER TABLE ordens_servico ALTER COLUMN cliente_id SET NOT NULL;
ALTER TABLE ordens_servico ALTER COLUMN motocicleta_id SET NOT NULL;
