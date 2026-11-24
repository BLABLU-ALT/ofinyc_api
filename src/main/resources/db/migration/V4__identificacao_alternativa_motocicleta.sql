ALTER TABLE motocicletas
    ALTER COLUMN placa DROP NOT NULL,
    ALTER COLUMN chassi DROP NOT NULL,
    ADD CONSTRAINT ck_motocicletas_identificacao CHECK (
        (placa IS NOT NULL OR chassi IS NOT NULL)
        AND (placa IS NULL OR btrim(placa) <> '')
        AND (chassi IS NULL OR btrim(chassi) <> '')
    );
