# ResumeSift — Resume Screening System

AI-powered resume screening built with **Java 17 + Spring Boot 3.2 + Apache PDFBox 3.0**.
No database needed — all data lives in-memory until the JD is cleared.

---

## Tech Stack

| Layer     | Technology                         |
|-----------|-------------------------------------|
| Backend   | Java 17, Spring Boot 3.2            |
| PDF Parse | Apache PDFBox 3.0.1                  |
| Frontend  | HTML5, CSS3, Vanilla JS (static)    |
| Build     | Maven                               |
| DB        | ❌ None required                    |

---

## Features

- ✅ Set a Job Description (role + skills/description text)
- ✅ Extract keywords from JD (single-word + multi-word tech phrases like "Spring Boot", "REST APIs", "machine learning")
- ✅ Upload PDF resumes one by one
- ✅ Extract **candidate name**, **email**, and **phone number** from each PDF
- ✅ Score each candidate: `(matched keywords / total keywords) × 100`
- ✅ Live ranked table — all candidates, descending score order
- ✅ Medal icons for Top 3 (🥇🥈🥉), numbered ranks for the rest
- ✅ Color-coded scores: Green ≥60%, Amber ≥30%, Red <30%
- ✅ Export ranked list as **CSV** (Rank, Name, Email, Phone, Score, Keywords)
- ✅ **Clear JD** button — wipes all candidates and resets session
- ✅ Data persists in-memory until JD is cleared (survives multiple uploads)
- ✅ Stats panel: total candidates, highest score, keyword count

---

## Prerequisites

- Java 17+
- Maven 3.6+

---

## Run the Project

```bash
# 1. Clone / unzip the project
cd ResumeScreeningSystem

# 2. Build
mvn clean package -DskipTests

# 3. Run
mvn spring-boot:run
# or
java -jar target/ResumeScreeningSystem-1.0.0.jar
```

Open **http://localhost:8080** in your browser.

---

## REST API Endpoints

| Method | Endpoint        | Description                                |
|--------|-----------------|--------------------------------------------|
| POST   | `/setJD`        | Set active JD. Body: `role`, `jd` (form)   |
| POST   | `/clearJD`      | Clear JD and all candidate data            |
| POST   | `/uploadResume` | Upload PDF. Body: `file` (multipart/form)  |
| GET    | `/candidates`   | Get all candidates sorted by score desc    |
| GET    | `/currentRole`  | Get active role + keywords                 |
| GET    | `/exportCSV`    | Download ranked candidates as CSV          |

---

## How Scoring Works

```
Score (%) = (Number of JD keywords found in resume / Total JD keywords) × 100
```

Keyword matching supports:
- Single-word tech terms: `java`, `docker`, `git`
- Multi-word tech phrases: `spring boot`, `rest apis`, `machine learning`, `ci/cd`
- Word-boundary aware matching (won't match "java" inside "javascript" for single words)

---

## Project Structure

```
ResumeScreeningSystem/
├── pom.xml
└── src/main/
    ├── java/com/resumescreening/
    │   ├── ResumeScreeningApplication.java   ← Entry point
    │   ├── model/
    │   │   └── Candidate.java                ← Data model (name, email, phone, score)
    │   ├── service/
    │   │   └── ResumeService.java            ← PDF extraction + scoring logic
    │   └── controller/
    │       └── ResumeController.java         ← REST endpoints
    └── resources/
        ├── application.properties
        └── static/
            └── index.html                    ← Frontend (served by Spring Boot)
```
