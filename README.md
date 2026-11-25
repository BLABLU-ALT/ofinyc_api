# OFICYNIC

## Requisitos

- Java 17
- PostgreSQL

## Como rodar

1. Crie um banco chamado `oficynic` no PostgreSQL.
2. Defina as variáveis de ambiente `DB_URL`, `DB_USER`, `DB_PASSWORD` e `APP_PASSWORD`.
3. Na pasta do projeto, rode:

```
mvnw spring-boot:run
```

4. Acesse http://127.0.0.1:8085 com o usuário `admin` e a senha definida em `APP_PASSWORD`.
