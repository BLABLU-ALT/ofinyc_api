-- Usuario é a superclasse de Administrador e Funcionario (diagrama de classes).
-- No banco, a herança fica em tabela única: a coluna tipo indica o perfil.
-- Os funcionários já cadastrados viram FUNCIONARIO ativos, sem acesso ao sistema.
-- As chaves estrangeiras de ordens_servico e movimentacoes_estoque acompanham a renomeação.
ALTER TABLE funcionarios RENAME TO usuarios;

ALTER TABLE usuarios
    ADD COLUMN tipo VARCHAR(20) NOT NULL DEFAULT 'FUNCIONARIO',
    ADD COLUMN login VARCHAR(60),
    ADD COLUMN senha VARCHAR(100),
    ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_usuarios_tipo CHECK (tipo IN ('ADMINISTRADOR', 'FUNCIONARIO')),
    ADD CONSTRAINT uk_usuarios_login UNIQUE (login),
    -- Login e senha são informados juntos; sem eles o usuário não acessa o sistema.
    ADD CONSTRAINT ck_usuarios_acesso CHECK ((login IS NULL) = (senha IS NULL));
