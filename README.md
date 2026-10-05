# Axoniq Platform Quickstart

## Prerequisites
- Java 21 or higher
- Maven 3.6 or higher
- Axon Server installation (or Docker, see below)
- An IDE like IntelliJ IDEA or Eclipse (optional)

## Getting Started

### Axon Server and Axoniq Insights with Docker Compose

`docker-compose.yml` starts Axon Server and Axoniq Insights, both connected to Axoniq Platform. The Platform
token is environment-specific and is read from a git-ignored `.env` file:

```bash
cp .env.example .env
# edit .env and set AXONIQ_PLATFORM_AUTHENTICATION to your Platform token
docker compose up -d
```

Compose refuses to start until the token is set. Axon Server's UI is on [http://localhost:8024](http://localhost:8024),
Insights on [http://localhost:8081](http://localhost:8081) (user `admin`, password `admin`). Axon Server initializes
itself on first start with a DCB-enabled `default` context, so the application can connect right away.

Both services take their non-secret settings from a properties file under `docker/`, mounted read-only into the
container. Edit the file and restart the service to apply:

| Service     | File                                     | Apply with                           |
|-------------|------------------------------------------|--------------------------------------|
| Axon Server | `docker/axonserver/axonserver.properties` | `docker compose restart axonserver` |
| Insights    | `docker/insights/application.properties` | `docker compose restart insights`   |

The GiftCard application itself reads its Axoniq Platform credentials from an `application.properties` file in the
project root, which is also git-ignored:

```properties
axoniq.platform.credentials=<your-platform-credentials>
```

### Running the application

Run the application using Maven:

```bash
mvn spring-boot:run
```

Alternatively, you can run the application from your IDE by running the `QuickstartApplication` class.

## Using the Application
You can interact with the application via the basic UI available on [http://localhost:8080](http://localhost:8080).
