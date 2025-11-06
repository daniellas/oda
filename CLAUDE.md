# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Open Dev Analytics (ODA) is a Scala-based data analytics platform for JIRA and GitLab metrics. It collects development data, processes it with Apache Spark, stores metrics in InfluxDB, and provides REST API access and Grafana visualizations.

## Technology Stack

- **Language**: Scala 2.12.9
- **Build**: Maven
- **Data Processing**: Apache Spark 2.4.4
- **API Server**: Vert.x 3.5.4
- **Storage**: InfluxDB (time-series), Local filesystem (JSON cache)
- **JSON**: JSON4S

## Build & Test Commands

```bash
# Compile
mvn compile

# Package (creates shaded uber-jar)
mvn package

# Run unit tests only (excludes IT tagged tests)
mvn test

# Run integration tests
mvn verify -P it

# Run specific test
mvn test -Dsuites="net.oda.jira.JiraClientSpec"

# Skip tests during build
mvn package -DskipTests

# Build Docker image
mvn package -P docker
```

## Running the Application

```bash
# Requires config.json in working directory
java -jar target/oda-2.2.0-SNAPSHOT.jar

# Or with Maven
mvn exec:java -Dexec.mainClass="net.oda.OdaApp"
```

## Architecture

### Data Flow
```
External API → Client → JSON File → Spark Processing → InfluxDB → Grafana
                                                     ↓
                                                REST API (on-demand)
```

### Package Structure

- **jira/**: JIRA integration (client, data loading, reporting)
  - `JiraClient`: Fetches issues using JIRA v3 search API with pagination
  - `JiraData`: Loads and processes work items
  - `JiraInflux`: Writes metrics to InfluxDB
  - `JiraReporter`: Generates analytics reports

- **gitlab/**: GitLab integration (client, data loading, reporting)
  - `GitlabClient`: Fetches projects, commits, merge requests
  - `GitlabData`: Loads and processes Git data
  - `GitlabInflux`: Writes metrics to InfluxDB

- **cfd/**: Cumulative Flow Diagram generation
  - `CfdReporter`: Calculates cycle time, throughput, WIP metrics
  - File-based caching to avoid regenerating unchanged reports

- **workitem/**: Core domain model for work items with status history

- **rest/client/**: Functional REST client abstraction
  - Trait-based fluent API for HTTP operations
  - `VertxHttpExecutor`: Vert.x-based implementation

- **vertx/**: HTTP server setup and routing

- **influx/**: InfluxDB connection and write operations

## Configuration

Create `config.json` based on `config.example.json`:

```json
{
  "jira": {
    "apiUrl": "https://your-jira.com/rest/api/3",
    "user": "your-email@company.com",
    "apiKey": "your-api-key",
    "storyPointsField": "customfield_10014",
    "estimateField": "customfield_10035",
    "epicNameField": "customfield_10005",
    "projects": {
      "PROJECT_ID": {
        "estimateMapping": { "S": 3, "M": 5, "L": 8, "XL": 21 },
        "referenceFlow": { "Backlog": -1, "To Do": 1, "In Progress": 2, "Done": 6 },
        "entryState": "To Do",
        "finalState": "Done",
        "stateMapping": { "IN REVIW": "In Review" }
      }
    }
  },
  "data": { "location": "path/to/data" },
  "reports": { "location": "path/to/reports" },
  "http": { "port": 8090 },
  "influxdb": { "host": "localhost", "port": 8086, "db": "oda" }
}
```

### Project Configuration

Each JIRA project requires:
- **estimateMapping**: Maps estimate labels to numeric values
- **referenceFlow**: Defines workflow states with numeric ordering
- **entryState**: First active state (when work begins)
- **finalState**: Final completed state
- **stateMapping**: Normalizes inconsistent state names

## REST API Endpoints

- `POST /oda/api/jira/:projectKey/download` - Download JIRA data
- `GET /oda/api/jira/projects` - List configured projects
- `GET /oda/api/jira/:projectKey/config` - Get project configuration
- `GET /oda/api/cfd/:projectKey` - Generate CFD report

## Key Patterns

### Functional Programming
- Immutable data structures
- Function composition
- Trait-based abstractions

### State Normalization
JIRA workflow states are normalized using `stateMapping` to handle typos and variations (e.g., "IN REVIW" → "In Review").

### Test Organization
- Unit tests: Run with `mvn test`
- Integration tests: Tagged with `IT`, excluded from default test runs
- Never run tests ending with `ITest`, `BTest`, `PTest` unless instructed

### JIRA API
- Uses JIRA v3 search API
- Pagination with `nextPageToken` (not `startAt`)
- Handles status history for work item flow analysis

### Caching Strategy
- Downloaded JSON data cached in `data.location`
- CFD reports cached in `reports.location`
- Both excluded from git via `.gitignore`

## Important Notes

1. **Configuration**: Application requires `config.json` in working directory at runtime

2. **Integration Tests**: Tests with `IT` tag must be run explicitly with `mvn verify -P it`

3. **Data Storage**: Ensure `data.location` and `reports.location` directories exist

4. **Email Mapping**: Use `emailMapping` in config to normalize user identities across systems

5. **Metrics Tracking**: System tracks AI-generated commits (looks for "Co-Authored-By: Claude")

6. **Timezone Awareness**: All time variables and database columns must be timezone-aware
