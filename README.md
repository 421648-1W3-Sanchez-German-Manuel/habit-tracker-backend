# Habit Tracker API

Backend REST API for tracking habits, logs, streaks, and completion metrics.

Persistence model:

- `User` is stored in PostgreSQL (JPA)
- `Habit` and `HabitLog` are stored in MongoDB
- Relationship is maintained by `userId` as a String UUID in Habit documents

## Tech stack

- Java 21
- Spring Boot
- Spring Data MongoDB
- Spring Data JPA
- PostgreSQL
- Lombok
- Jakarta Validation

## Base URL

`http://localhost:8080`

## API examples

### 1) Create user

**Request**

`POST /users`

```json
{
	"username": "manu_dev",
	"email": "manu.dev@example.com"
}
```

**Response (201 Created)**

```json
{
	"id": "7d1bf359-e8cf-4812-9e50-106800f52bb6",
	"username": "manu_dev",
	"email": "manu.dev@example.com"
}
```

### 2) Create habit

**Request**

`POST /habits`

```json
{
	"userId": "7d1bf359-e8cf-4812-9e50-106800f52bb6",
	"name": "Drink 2L water",
	"type": "BOOLEAN",
	"frequency": "DAILY"
}
```

**Response (201 Created)**

```json
{
	"id": "65f8cc7b3e4f9d2bc3ab1202",
	"userId": "7d1bf359-e8cf-4812-9e50-106800f52bb6",
	"name": "Drink 2L water",
	"type": "BOOLEAN",
	"frequency": "DAILY",
	"createdAt": "2026-03-20T14:32:51.248Z"
}
```

### 3) Add log

**Request**

`POST /logs`

```json
{
	"habitId": "65f8cc7b3e4f9d2bc3ab1202",
	"date": "2026-03-20",
	"value": true
}
```

**Response (201 Created)**

```json
{
	"id": "65f8ccd53e4f9d2bc3ab1203",
	"habitId": "65f8cc7b3e4f9d2bc3ab1202",
	"date": "2026-03-20",
	"value": true
}
```

### 4) Get logs

**Request**

`GET /logs/habit/65f8cc7b3e4f9d2bc3ab1202?from=2026-03-14&to=2026-03-20`

**Response (200 OK)**

```json
[
	{
		"id": "65f8cb113e4f9d2bc3ab11f8",
		"habitId": "65f8cc7b3e4f9d2bc3ab1202",
		"date": "2026-03-14",
		"value": true
	},
	{
		"id": "65f8cbc13e4f9d2bc3ab11fd",
		"habitId": "65f8cc7b3e4f9d2bc3ab1202",
		"date": "2026-03-18",
		"value": true
	},
	{
		"id": "65f8ccd53e4f9d2bc3ab1203",
		"habitId": "65f8cc7b3e4f9d2bc3ab1202",
		"date": "2026-03-20",
		"value": true
	}
]
```

### 5) Get streak

**Request**

`GET /habits/65f8cc7b3e4f9d2bc3ab1202/streak`

**Response (200 OK)**

```json
4
```

### 6) Get completion %

**Request**

`GET /habits/65f8cc7b3e4f9d2bc3ab1202/completion`

**Response (200 OK)**

```json
71.43
```

## Running the project

### Prerequisites

- Java 21
- Maven 3.9+
- Docker Desktop or Docker Engine with Compose support

### 1) Start infrastructure services

```bash
docker compose up -d mongo postgres mongo-express ollama
```

MongoDB will be available at `localhost:27018`.
PostgreSQL will be available at `localhost:5434`.
Mongo Express UI will be available at `http://localhost:8081`.
Ollama will be available at `http://localhost:11434`.

The backend is no longer started by Docker Compose. Run it separately from this folder with Maven.

### 2) Configure `.env`

Create local environment file from the template:

PowerShell:

```powershell
Copy-Item .env.example .env
```

Bash:

```bash
cp .env.example .env
```

Required variables for Docker Compose and host-run app startup:

```env
MONGO_INITDB_ROOT_USERNAME=change_me_mongo_user
MONGO_INITDB_ROOT_PASSWORD=change_me_mongo_password
MONGO_INITDB_DATABASE=habitdb

POSTGRES_DB=habit_tracker
POSTGRES_USER=change_me_pg_user
POSTGRES_PASSWORD=change_me_pg_password

ME_CONFIG_MONGODB_ADMINUSERNAME=change_me_mongo_user
ME_CONFIG_MONGODB_ADMINPASSWORD=change_me_mongo_password
ME_CONFIG_MONGODB_SERVER=mongo
ME_CONFIG_MONGODB_AUTH_DATABASE=admin

SPRING_DATA_MONGODB_URI=mongodb://change_me_mongo_user:change_me_mongo_password@localhost:27018/habitdb?authSource=admin
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/habit_tracker
SPRING_DATASOURCE_USERNAME=change_me_pg_user
SPRING_DATASOURCE_PASSWORD=change_me_pg_password
SPRING_JPA_HIBERNATE_DDL_AUTO=update
SPRING_JPA_SHOW_SQL=true
APP_JWT_SECRET=change_me_jwt_secret_at_least_32_chars
APP_JWT_EXPIRATION_MS=86400000
APP_SEED_ENABLED=true
APP_OLLAMA_BASE_URL=http://localhost:11434
```

Security note: do not commit real secrets. Keep real values only in local `.env`.

### 3) Run the API

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

### Startup seed data

On startup, the app inserts demo data automatically if the database is empty:

- 1 user in PostgreSQL (`demo_user`)
- 3 habits in MongoDB (BOOLEAN, NUMBER, TEXT)
- 8 habit logs in MongoDB

This seed runs only when there are no users, habits, or logs yet.

To disable the seed:

```env
APP_SEED_ENABLED=false
```

### Stop local services

```bash
docker compose down
```
