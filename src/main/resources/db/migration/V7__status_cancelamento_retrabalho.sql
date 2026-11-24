ALTER TABLE ordens_servico DROP CONSTRAINT ck_os_situacao;
ALTER TABLE ordens_servico DROP CONSTRAINT ck_os_finalizacao;
ALTER TABLE ordens_servico ALTER COLUMN situacao DROP DEFAULT;

UPDATE ordens_servico SET situacao = 'ORCAMENTO' WHERE situacao = 'ABERTA';
UPDATE ordens_servico SET situacao = 'CONCLUIDA' WHERE situacao = 'FINALIZADA';

ALTER TABLE ordens_servico
    ALTER COLUMN situacao SET DEFAULT 'ORCAMENTO',
    ADD COLUMN ordem_origem_id BIGINT REFERENCES ordens_servico(id),
    ADD CONSTRAINT ck_os_situacao CHECK (
        situacao IN ('ORCAMENTO', 'CANCELADA', 'EM_ANDAMENTO', 'CONCLUIDA', 'RETRABALHO')
    ),
    ADD CONSTRAINT ck_os_finalizacao CHECK (
        (situacao = 'CONCLUIDA' AND data_finalizacao IS NOT NULL)
        OR (situacao <> 'CONCLUIDA' AND data_finalizacao IS NULL)
    ),
    ADD CONSTRAINT ck_os_origem CHECK (ordem_origem_id IS NULL OR ordem_origem_id <> id);

CREATE INDEX idx_os_origem ON ordens_servico(ordem_origem_id);
