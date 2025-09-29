# NailSalon-SpringBoot-API

A backend service for managing a nail salon. This RESTful API handles client management, appointment booking, user registration, and login with secure authentication.

## Features
- Secure authentication & authorization using **Spring Security**, **JWT**, and **BCrypt**.
- Layered architecture: Controller, Service, Repository, DTO.
- Centralized error handling with Global Exception Handling.
- Unit tests with **JUnit** and **Mockito**.
- Create bookings for guest or registered user.
- Admin features: view, update, delete bookings.
- Slot validation (limit bookings per hour).

## Technologies
Java, Spring Boot, Spring Security, JWT, REST, JPA/Hibernate, MySQL, Docker, JUnit, Mockito

## Getting Started

### 1. Clone the Repository
### 2. Configure Application Properties
- Copy the example file and rename: cp src/main/resources/application.properties.example src/main/resources/application.properties
- Edit application.properties with your database credentials and JWT config if needed.
### 3. Configure Environment Variables (Optional)
- Copy .env.example to .env and fill in any secrets or overrides for local development: cp .env.example .env
- This is useful if you prefer using Docker environment variables instead of editing application.properties.
### 4. Run With Docker (Recommended)
- Make sure Docker is installed.
- Build and run services: docker-compose up --build
- Services:
-- MySQL: mysql:8.0.41
-- Booking service: Spring Boot app on port 8080
### 5. Run Locally Without Docker
- Make sure MySQL is running locally.
- Adjust spring.datasource.url in application.properties.
- Run: mvn spring-boot:run

## Postman Collection
- Import Nagelstudio API- CRUD.postman_collection.json into Postman: Open Postman → Import → File → Select the JSON file.
- Make sure the backend is running at http://localhost:8080.
- Test API endpoints:
-- Authentication: register, login
-- Bookings: create, update, view, delete
-- User controll: create, update, view, delete
