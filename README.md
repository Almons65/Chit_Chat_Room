# Chit_Chat_Room

A real-time, multi-tier web chat application featuring real-time messaging via WebSockets and persistent data storage. 

## Prerequisites
To run this application, you must have the following installed on your machine:
* [Docker](https://docs.docker.com/get-docker/)
* [Docker Compose](https://docs.docker.com/compose/install/)

## Installation and Build Instructions
This project is fully containerized. You do not need to install Java or PostgreSQL locally to run it.

1. Clone or extract the project source code to your local machine.
2. Open a terminal and navigate to the root directory of the project (where the `docker-compose.yml` file is located).
3. Build the application and database containers by running:
   ```bash
   docker-compose build

## How to run the system
1. Start the system in detached mode by running:
    ```bash
    docker-compose up -d

2. Wait a few seconds for the PostgreSQL to intialize and the backend to connect.
3. Open your web browser and navigate to: http://localhost:8080
4. To stop running the system:
    ```bash
    docker-compose down