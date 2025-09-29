# NailSalon-SpringBoot-API

A backend service for managing a nail salon. This RESTful API handles client management, appointment booking, user registration, and login with secure authentication.

## Features
- Secure authentication & authorization using **Spring Security**, **JWT**, and **BCrypt**.
- Layered architecture: Controller, Service, Repository, DTO.
- Centralized error handling with Global Exception Handling.
- Unit tests with **JUnit** and **Mockito**.
- Create bookings for guest or registered user
- Admin features: view, update, delete bookings
- Slot validation (limit bookings per hour)

## Technologies
Java, Spring Boot, Spring Security, JWT, REST, JPA/Hibernate, MySQL, Docker, JUnit, Mockito

## How to Run
1. Clone the repository
2. Configure your database connection in application.properties.
3. Run the application: mvn spring-boot:run
