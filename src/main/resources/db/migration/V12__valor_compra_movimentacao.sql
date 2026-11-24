-- Valor unitário de compra informado nas entradas manuais; movimentos antigos ficam sem valor conhecido.
ALTER TABLE movimentacoes_estoque ADD COLUMN valor_unitario NUMERIC(14,2)
    CONSTRAINT ck_movimento_valor_unitario CHECK (valor_unitario IS NULL OR valor_unitario >= 0);

-- Somente entrada tem valor de compra.
ALTER TABLE movimentacoes_estoque ADD CONSTRAINT ck_movimento_valor_so_entrada
    CHECK (valor_unitario IS NULL OR tipo = 'ENTRADA');
