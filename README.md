# Patient Management System

This repository contains a microservices-based Patient Management System. The application is designed to handle patient data, authentication, billing, and analytics through a distributed architecture. The system leverages technologies like Spring Boot, Docker, gRPC, and Apache Kafka. The entire infrastructure is defined as code using AWS CDK for deployment on LocalStack.

## System Architecture

The system is composed of several microservices that work together, communicating via REST, gRPC, and a message queue. An API Gateway serves as the single entry point for all client requests.

<img width="1103" height="563" alt="Screenshot 2025-09-20 at 6 15 32 PM" src="https://github.com/user-attachments/assets/bebeb11a-f97b-4615-a789-f3d582833b1b" />



## Technologies Used

*   **Backend:** Java 17, Spring Boot
*   **Microservices:** Spring Cloud Gateway, Spring Web, Spring Security
*   **Communication:** REST, gRPC, Apache Kafka
*   **Database:** PostgreSQL, H2 (for local testing)
*   **Authentication:** JWT (JSON Web Tokens)
*   **Containerization:** Docker
*   **Infrastructure as Code:** AWS CDK, LocalStack
*   **Build Tool:** Maven
*   **Testing:** JUnit 5, REST Assured

## Microservices

The application is divided into the following microservices:

### 1. API Gateway (`api-gateway`)
*   **Description:** The single entry point for all incoming traffic. It routes requests to the appropriate backend services.
*   **Port:** `4004`
*   **Functionality:**
    *   Routes `/auth/**` requests to the `auth-service`.
    *   Routes `/api/patients/**` requests to the `patient-service`.
    *   Secures the patient service endpoint using a `JwtValidationGatewayFilterFactory`, which validates the `Authorization` header by calling the `auth-service`.

### 2. Authentication Service (`auth-service`)
*   **Description:** Manages user authentication and token generation.
*   **Port:** `4005`
*   **Endpoints:**
    *   `POST /login`: Authenticates a user with email and password, returning a JWT.
    *   `GET /validate`: Validates a given JWT. This is used internally by the API Gateway.
*   **Database:** Uses a PostgreSQL database to store user credentials. A test user is pre-populated via `data.sql`.

### 3. Patient Service (`patient-service`)
*   **Description:** The core service for managing patient records.
*   **Port:** `4000`
*   **Functionality:**
    *   Provides full CRUD (Create, Read, Update, Delete) operations for patients via a RESTful API at `/patients`.
    *   On patient creation, it makes a gRPC call to the `billing-service` to create a corresponding billing account.
    *   Publishes a `PatientEvent` (using Protobuf serialization) to the `patient` Kafka topic when a new patient is created.
*   **Database:** Uses a PostgreSQL database, pre-populated with sample patient data from `data.sql`.

### 4. Billing Service (`billing-service`)
*   **Description:** A gRPC service responsible for handling patient billing.
*   **Ports:** `4001` (HTTP), `9001` (gRPC)
*   **Functionality:**
    *   Exposes a `CreateBillingAccount` gRPC method.
    *   When called by the `patient-service`, it logs the request and returns a confirmation response.

### 5. Analytics Service (`analytics-service`)
*   **Description:** Consumes events for data analysis purposes.
*   **Port:** `4002`
*   **Functionality:**
    *   Listens to the `patient` Kafka topic.
    *   Consumes `PatientEvent` messages, deserializes them from Protobuf format, and logs the event details.

## Infrastructure as Code (`infrastructure`)

The `infrastructure` module contains an AWS CDK application written in Java. It defines all the necessary cloud resources to run the system in a simulated AWS environment using LocalStack.

The CDK stack provisions:
*   A Virtual Private Cloud (VPC).
*   Two PostgreSQL RDS database instances (for `auth-service` and `patient-service`).
*   An Amazon MSK (Managed Streaming for Kafka) cluster.
*   An ECS cluster with Fargate services for each microservice, configured with the correct environment variables and networking.

## How to Run

### Prerequisites
*   Java 17
*   Apache Maven
*   Docker and Docker Compose
*   AWS CLI (configured for LocalStack)
*   AWS CDK CLI (`npm install -g aws-cdk`)

### 1. Build Docker Images
Each microservice has its own `Dockerfile`. Build an image for each service by navigating into its directory and running the `docker build` command.

```bash
# Example for auth-service
cd auth-service/
docker build -t auth-service .

# Repeat for other services:
# - patient-service
# - billing-service
# - analytics-service
# - api-gateway
```

### 2. Deploy Infrastructure with LocalStack & CDK
1.  **Start LocalStack:** Ensure your Docker environment is running and start LocalStack.

2.  **Deploy the CDK Stack:**
    Navigate to the `infrastructure` directory and deploy the stack. This will create all the necessary resources (databases, Kafka, ECS services) inside your LocalStack container.

    ```bash
    cd infrastructure/
    
    # Bootstrap CDK for your local environment (only needs to be done once)
    cdk bootstrap
    
    # Deploy the patient management system stack
    cdk deploy localstack
    ```
    This process will take several minutes as it provisions all the services.

### 3. Using the API
Once deployed, the system is accessible through the API Gateway at `http://localhost:4004`. The repository includes `.http` files in the `api-requests` and `grpc-requests` directories, which can be used with tools like the IntelliJ IDEA HTTP Client.

**1. Login and Get a Token**
Send a `POST` request to `/auth/login` to get an authentication token.

```http
### api-requests/auth-service/login-request.http
POST http://localhost:4004/auth/login
Content-type: application/json

{
  "email": "testuser@test.com",
  "password": "password123"
}
```

The response will contain a JWT.

**2. Access a Protected Endpoint**
Use the obtained token in the `Authorization` header to access protected endpoints like fetching all patients.

```http
### api-requests/patient-service/getAllPatients.http
GET http://localhost:4004/api/patients
Authorization: Bearer <YOUR_JWT_TOKEN_HERE>
```

**3. Make a gRPC Request**
You can also test the gRPC endpoint for the `billing-service` directly.

```http
### grpc-requests/billing-service/create-billing-account.http
GRPC localhost:9001/BillingService/CreateBillingAccount
Content-Type: application/json

{
  "patientId": "1233",
  "name" : "Phani Teja U",
  "email" : "phanitejau@gmail.com"
}
```

## Testing

The `integration-tests` module contains REST Assured tests to verify the end-to-end functionality of the authentication and patient services. These tests simulate the API flow by first acquiring a token and then using it to access protected resources.

To run the tests, navigate to the `integration-tests` directory and execute:
```bash
mvn test
