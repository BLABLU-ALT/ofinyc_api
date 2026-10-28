-- Parecer técnico separado da orientação do cliente (coluna descricao).
ALTER TABLE ordens_servico ADD COLUMN parecer_tecnico VARCHAR(1000);

-- Preço de custo definido pelo administrador; nulo em produtos antigos.
ALTER TABLE produtos ADD COLUMN preco_custo NUMERIC(14,2)
    CONSTRAINT ck_produto_preco_custo CHECK (preco_custo IS NULL OR preco_custo >= 0);

-- Custo copiado no momento da inclusão; itens antigos ficam sem custo conhecido.
ALTER TABLE itens_produto_os ADD COLUMN custo_unitario NUMERIC(14,2)
    CONSTRAINT ck_item_produto_custo CHECK (custo_unitario IS NULL OR custo_unitario >= 0);
