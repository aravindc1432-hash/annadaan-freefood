# 🍱 AnnaDaan — Free Food India

A web application where anyone across India can **share free food events** and others can **find the nearest ones** sorted by distance.

---

## ✨ Features

- 📢 **Post** free food events with food description, date, time, location
- ⏰ **Auto-expiry** — events are automatically deleted once the closing time passes
- 📍 **Location-based sorting** — events sorted nearest-first using your GPS (Haversine formula)
- ✏️ **Edit / Delete** any event
- 🔍 **Search** by food, city, or provider name
- 🗺️ **Google Maps** link for each event
- 🔄 **Scheduled cleanup** every 15 minutes removes expired events
- 💾 **SQLite** — zero-config embedded database

---

## 🗂 Project Structure

```
freefood-app/
├── pom.xml
└── src/
    └── main/
        ├── java/com/freefood/
        │   ├── model/
        │   │   └── FoodEvent.java          # Data model
        │   ├── dao/
        │   │   └── FoodEventDAO.java       # DB operations
        │   ├── util/
        │   │   ├── DatabaseUtil.java       # SQLite connection + schema
        │   │   └── DistanceUtil.java       # Haversine distance formula
        │   └── servlet/
        │       ├── FoodEventServlet.java   # REST API  (/api/events/*)
        │       ├── CORSFilter.java         # CORS headers
        │       └── AppStartupListener.java # DB init + cleanup scheduler
        └── webapp/
            ├── index.html                  # Frontend SPA
            ├── css/style.css
            ├── js/app.js
            └── WEB-INF/
                └── web.xml
```

---

## 🚀 Build & Run

### Prerequisites
- Java 11+
- Maven 3.6+
- Apache Tomcat 10.x (for production deploy)

### 1. Clone & Build

```bash
git clone <your-repo-url>
cd freefood-app
mvn clean package
```

This creates `target/freefood-app.war`.

### 2. Run Locally (Quick Dev Mode)

```bash
mvn tomcat7:run
```

Open: **http://localhost:8080/freefood**

### 3. Deploy to Tomcat Server

```bash
# Copy WAR to Tomcat webapps folder
cp target/freefood-app.war /opt/tomcat/webapps/

# Start Tomcat
/opt/tomcat/bin/startup.sh
```

Open: **http://your-server-ip:8080/freefood-app**

---

## 🌐 REST API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/events` | All active events |
| `GET` | `/api/events?lat=XX&lon=YY` | Active events sorted by distance |
| `GET` | `/api/events/{id}` | Get single event |
| `POST` | `/api/events` | Create new event |
| `PUT` | `/api/events/{id}` | Update event |
| `DELETE` | `/api/events/{id}` | Delete event |
| `DELETE` | `/api/events/cleanup` | Manually remove expired events |

### Sample POST Body

```json
{
  "providerName": "Ramesh Kumar",
  "contactNumber": "9876543210",
  "foodDescription": "Rice, Dal, Sabzi, Roti — full meal",
  "address": "Near Shivaji Statue, MG Road",
  "city": "Mumbai",
  "state": "Maharashtra",
  "latitude": 19.0760,
  "longitude": 72.8777,
  "eventDate": "2025-01-15",
  "startTime": "12:00",
  "endTime": "14:00",
  "quantity": 200,
  "notes": "Free for all. Bring your own container."
}
```

---

## 🗄️ Database

- **Engine**: SQLite (embedded, no setup needed)
- **Location**: `~/freefood/freefood.db` (user home directory)
- **Auto-cleanup**: Events past their `end_time` are deleted automatically
  - On every fetch request
  - By background scheduler every 15 minutes

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 11, Jakarta Servlet 5.0 |
| Database | SQLite via xerial JDBC |
| JSON | Jackson Databind |
| Build | Maven 3 |
| Server | Apache Tomcat 10 |
| Frontend | Vanilla HTML / CSS / JS |

---

## 📱 How to Use

1. Open the app
2. Click **"📍 Use My Location"** to enable distance-sorted results
3. Browse food events near you, sorted by kilometres
4. Click **"+ Share Free Food"** to post your own event
5. Fill in food details, location, and timings
6. Once the closing time passes, the event is automatically removed

---

## 🔧 Configuration

To change the port or context path, edit `pom.xml`:

```xml
<configuration>
    <port>8080</port>
    <path>/freefood</path>
</configuration>
```

---

*Built with ❤️ for India — AnnaDaan means "donation of food" in Sanskrit*

---

## 🐳 Docker

### Option A — Docker Compose (recommended, one command)

```bash
# Build image and start container
docker compose up --build

# Run in background
docker compose up --build -d

# Stop
docker compose down

# Stop and wipe all data (DB + photos)
docker compose down -v
```

App runs at → **http://localhost:8080**
Admin panel → **http://localhost:8080/admin/**

---

### Option B — Plain Docker

```bash
# 1. Build the image
docker build -t annadaan:latest .

# 2. Run it
docker run -d \
  --name annadaan-app \
  -p 8080:8080 \
  -v annadaan-data:/data/freefood \
  -e JAVA_OPTS="-Xms256m -Xmx512m" \
  --restart unless-stopped \
  annadaan:latest

# 3. View logs
docker logs -f annadaan-app

# 4. Stop / remove
docker stop annadaan-app
docker rm annadaan-app
```

---

### Push to DockerHub (for Jenkins CI/CD)

```bash
docker build -t <your-dockerhub-username>/annadaan:latest .
docker push <your-dockerhub-username>/annadaan:latest
```

### Pull and run on EC2 / any server

```bash
docker pull <your-dockerhub-username>/annadaan:latest
docker run -d -p 8080:8080 -v annadaan-data:/data/freefood <your-dockerhub-username>/annadaan:latest
```

---

### Data Persistence

| What | Where inside container | Volume path |
|------|------------------------|-------------|
| SQLite database | `/data/freefood/freefood.db` | `annadaan-data` volume |
| Uploaded food photos | `/data/freefood/uploads/` | same volume |

The named volume `annadaan-data` survives `docker compose down`.
Only `docker compose down -v` deletes it.

---

### Jenkins Pipeline (DockerHub push)

Add this stage to your existing Jenkinsfile:

```groovy
pipeline {
    agent any
    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-creds')
        IMAGE_NAME = "youruser/annadaan"
    }
    stages {
        stage('Build WAR') {
            steps { sh 'mvn clean package -DskipTests' }
        }
        stage('Docker Build') {
            steps { sh "docker build -t ${IMAGE_NAME}:${BUILD_NUMBER} -t ${IMAGE_NAME}:latest ." }
        }
        stage('Docker Push') {
            steps {
                sh "echo $DOCKERHUB_CREDENTIALS_PSW | docker login -u $DOCKERHUB_CREDENTIALS_USR --password-stdin"
                sh "docker push ${IMAGE_NAME}:${BUILD_NUMBER}"
                sh "docker push ${IMAGE_NAME}:latest"
            }
        }
        stage('Deploy') {
            steps {
                sh """
                    docker stop annadaan-app || true
                    docker rm   annadaan-app || true
                    docker run -d --name annadaan-app \\
                        -p 8080:8080 \\
                        -v annadaan-data:/data/freefood \\
                        --restart unless-stopped \\
                        ${IMAGE_NAME}:latest
                """
            }
        }
    }
}
```
