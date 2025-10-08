# 🗕️ CalSync: Automated College Timetable Subscriptions

**CalSync** is a backend service designed to solve a common problem for college students — keeping their personal calendars synchronized with their official, often-changing university timetables.

This service securely logs into a student's university portal, scrapes their class schedule and the academic calendar, and generates a **dynamic, auto-updating iCalendar (.ics)** subscription link.
The link works with all major calendar clients — **Google Calendar, Apple Calendar, and Outlook** — providing a true “set it and forget it” solution.

---

## ✨ Key Features

* **Dynamic iCalendar Subscription** – Provides a stable URL that calendar clients can subscribe to for automatic updates.
* **Real-time Web Scraping** – Simulates a browser to perform a secure login and scrape data directly from the source.
* **Intelligent Schedule Merging** – Merges personal timetables with the college’s academic planner, automatically handling holidays and day orders.
* **Secure Credential Handling** – Encrypts passwords using AES before storage (never saved in plain text).
* **Cross-Platform Compatibility** – Fully compliant with [RFC 5545](https://datatracker.ietf.org/doc/html/rfc5545), works across Google, Apple, and Outlook.
* **Production-Ready Deployment** – Hosted on **AWS Elastic Beanstalk** with a **PostgreSQL** database on **AWS RDS**.

---

## ⚙️ How It Works (Architecture)

CalSync is built on a robust **three-tiered architecture**:

### 1. Authentication & Web Scraping

* Programmatically logs into the student portal: [academia.srmist.edu.in](https://academia.srmist.edu.in)
* Handles cookies, CSRF tokens, and secure session authentication
* Scrapes HTML data from **“My Time Table”** and **“Academic Planner”**

### 2. Data Processing & Calendar Generation

* Uses **Jsoup** to parse HTML and extract:

  * Student’s class schedule
  * Semester academic calendar
* Core logic matches **day orders** to **class slots**, adjusting for holidays
* Generates valid `.ics` files using **iCal4j**, ensuring:

  * Stable UIDs
  * No duplicate events
  * Proper time and recurrence formatting

### 3. Secure Subscription Management

* Passwords are encrypted (AES) and stored in **PostgreSQL**
* Generates a **unique subscription token (UUID)** for each user
* The token creates a **public subscription URL**:

  ```
  https://your-deployed-app-url.com/api/calendar/{token}
  ```
* When accessed, the backend decrypts credentials, re-scrapes data, and regenerates the calendar in real-time.

---

## 🛠️ Tech Stack

| Technology                       | Purpose                                    |
| -------------------------------- | ------------------------------------------ |
| **Java 17 & Spring Boot**        | Core application framework                 |
| **Spring Data JPA & PostgreSQL** | Database access and persistence            |
| **Spring Security**              | Foundational security features             |
| **Jsoup**                        | HTML parsing and web scraping              |
| **iCal4j**                       | iCalendar (.ics) file generation           |
| **Maven**                        | Dependency management and build automation |

---

## 🔌 API Endpoints

### **POST** `/api/subscribe`

Creates a new calendar subscription.

**Request Body:**

```json
{
  "username": "your_academia_username",
  "password": "your_academia_password"
}
```

**Success Response (200 OK):**

```json
{
  "subscriptionUrl": "https://your-deployed-app-url.com/api/calendar/a1b2c3d4-e5f6-..."
}
```

---

### **GET** `/api/calendar/{token}`

Fetches the auto-updating iCalendar feed.

**URL Parameter:**

* `token`: Unique subscription token returned by `/api/subscribe`

**Response (200 OK):**

* Returns `.ics` calendar data
* `Content-Type: text/calendar`

---

## 🚀 Running Locally

### **Prerequisites**

* Java 17
* Apache Maven
* PostgreSQL installed and running

### **Setup**

```bash
git clone https://github.com/your-username/calsync-backend.git
cd calsync-backend
```

Create a local PostgreSQL database (e.g. `calsyncdb`).

In `src/main/resources/`, create an **`application.properties`** file (ignored by Git):

```properties
# Server Port
server.port=8080

# PostgreSQL Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/calsyncdb
spring.datasource.username=your_postgres_username
spring.datasource.password=your_postgres_password

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update

# AES Encryption Key (must be 16 characters)
calsync.encryption.key=ThisIsASecretKey
```

### **Run the Application**

```bash
./mvnw spring-boot:run
```

The app will be available at:
👉 [http://localhost:8080](http://localhost:8080)



---

**CalSync** — *Seamless. Secure. Always in Sync.*
