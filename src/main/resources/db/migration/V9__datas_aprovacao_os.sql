ALTER TABLE ordens_servico ADD COLUMN data_criacao date;
ALTER TABLE ordens_servico ADD COLUMN data_aprovacao date;
ALTER TABLE ordens_servico ADD COLUMN data_abertura_anterior date;
COMMENT ON COLUMN ordens_servico.data_abertura_anterior IS 'Data registrada antes da aprovação de OS legada; não comprova a data de criação';
