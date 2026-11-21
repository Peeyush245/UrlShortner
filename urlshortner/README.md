# URL Shortener

A Spring Boot REST API that shortens URLs, with custom aliases, expiry (TTL), deactivation and hit counts. Data is kept in memory, so it is lost when the app stops.

## Run the app locally

1. Install **JDK 17 or newer** and check it with:
   ```bash
   java -version
   ```
2. Go to the project folder:
   ```bash
   cd urlshortner
   ```
3. Start the app (the first run downloads Maven and dependencies):
   ```bash
   ./mvnw spring-boot:run
   ```
4. Wait for `Started Main` in the log.
5. Open Swagger UI to try the API: **http://localhost:8082/swagger-ui.html**
6. Stop the app with `Ctrl+C`.


> **Tips**
> - Swagger shows "Failed to fetch" for `GET /{code}` because the browser follows the redirect to another site. Use `curl -i` or a browser tab to test redirects.
> - In a dev container, forward port `8082` in the **Ports** panel first.
> - To use another port: `SERVER_PORT=9090 ./mvnw spring-boot:run`

## Run the tests

1. From the project folder, run:
   ```bash
   ./mvnw test
   ```
2. Look for `BUILD SUCCESS` and the test counts at the end of the output.
3. If you see `NoClassDefFoundError` or odd compile errors after editing, rebuild from scratch:
   ```bash
   ./mvnw clean test
   ```

Test reports are written to `target/surefire-reports/`.

## Architecture
<img width="1040" height="1065" alt="image" src="https://github.com/user-attachments/assets/1cbbff76-4283-42db-8c3e-d67cce28e960" />



```

| Endpoint | What it does |
|---|---|
| `POST /api/shortUrl` | Create a short link (`url`, optional `alias`, optional `ttl` in seconds) |
| `GET /{code}` | Redirect to the original URL (404 if unknown, expired or deactivated) |
| `GET /api/shortUrl/{code}` | Metadata: original URL, code, created time, hit count, status |
| `POST /api/shortUrl/{code}/deactivate` | Deactivate a short link |
