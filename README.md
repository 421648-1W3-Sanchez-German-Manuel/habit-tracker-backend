# Habit Tracker API

Backend REST API for tracking habits, logs, streaks, and completion metrics.

## Tech stack

- Java 21
- Spring Boot
- Spring Data MongoDB
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
	"id": "65f8cc4c3e4f9d2bc3ab1201",
	"username": "manu_dev",
	"email": "manu.dev@example.com"
}
```

### 2) Create habit

**Request**

`POST /habits`

```json
{
	"userId": "65f8cc4c3e4f9d2bc3ab1201",
	"name": "Drink 2L water",
	"type": "BOOLEAN",
	"frequency": "DAILY"
}
```

**Response (201 Created)**

```json
{
	"id": "65f8cc7b3e4f9d2bc3ab1202",
	"userId": "65f8cc4c3e4f9d2bc3ab1201",
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
- Docker Desktop

### 1) Start Docker Mongo

```bash
docker compose up -d mongo mongo-express
```

MongoDB will be available at `localhost:27017`.
Mongo Express UI will be available at `http://localhost:8081`.

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

Current required variable:

```env
SPRING_DATA_MONGODB_URI=mongodb://root:rootpassword@localhost:27017/habitdb?authSource=admin
```

### 3) Run the API

```bash
mvn spring-boot:run
```

The API starts at `http://localhost:8080`.

### Stop local services

```bash
docker compose down
```
