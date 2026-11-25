# OFICYNIC

Precisa de JDK 17+ e PostgreSQL. As tabelas são criadas pelo Flyway na primeira execução.

Crie o banco:

```sql
CREATE USER oficynic WITH PASSWORD 'troque-esta-senha';
CREATE DATABASE oficynic OWNER oficynic;
```

Defina as variáveis e rode (Windows PowerShell; no Linux use `export` e `./mvnw`):

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/oficynic"; $env:DB_USER="oficynic"; $env:DB_PASSWORD="troque-esta-senha"; $env:APP_PASSWORD="escolha-uma-senha"
.\mvnw.cmd spring-boot:run
```

Acesse http://127.0.0.1:8085 com o usuário `admin` e a senha do `APP_PASSWORD`.
