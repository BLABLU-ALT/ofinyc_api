ALTER TABLE ordens_servico
    ADD CONSTRAINT ck_os_retrabalho_origem CHECK (
        situacao <> 'RETRABALHO' OR ordem_origem_id IS NOT NULL
    );
