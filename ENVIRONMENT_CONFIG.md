an# Environment Variables Configuration

This document explains how to configure the X-Men application using environment variables.

## Setup

1. Copy the `.env.example` file to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Modify the `.env` file with your desired configuration values.

## Available Environment Variables

### Server Configuration
- `SERVER_PORT` - The port on which the Spring Boot server runs (default: 8081)
- `APP_NAME` - The application name (default: X-Men)

### API Configuration
- `API_BASE_URL` - Base URL for the API (default: http://localhost:8081)
- `API_GENERATE_MUTATIONS_ENDPOINT` - Endpoint for mutation generation (default: /api/generateMutations)
- `API_FULL_URL` - Complete API URL (default: http://localhost:8081/api/generateMutations)

### CORS Configuration
- `CORS_ALLOWED_ORIGINS` - Comma-separated list of allowed CORS origins (default: http://localhost:808[2-9],http://localhost:80[9-9][0-9])
- `CORS_MAX_AGE` - CORS preflight cache duration in seconds (default: 3600)

## How It Works

The application uses Spring Boot's built-in environment variable support:

1. **Spring Boot Configuration**: The `application.yaml` file uses `${VARIABLE_NAME:default_value}` syntax to read environment variables with fallback defaults.

2. **Java System Properties**: The JavaFX frontend reads environment variables using `System.getenv()` and `System.getProperty()` with fallback defaults.

3. **CORS Configuration**: The Spring configuration uses `@Value` annotations to inject environment variables into the CORS settings.

## Running the Application

The application will automatically pick up environment variables from:
1. System environment variables
2. The `.env` file (if present)
3. Java system properties (passed with `-D` flags)

### Example: Running with custom port
```bash
# Using environment variable
export SERVER_PORT=9090
mvn spring-boot:run

# Or using system property
mvn spring-boot:run -DSERVER_PORT=9090
```

## Security Note

- The `.env` file is included in `.gitignore` to prevent committing sensitive configuration
- Use `.env.example` as a template for new environments
- Never commit actual `.env` files to version control
