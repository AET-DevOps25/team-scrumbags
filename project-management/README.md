# Setup 

## Database

### Run database container using Docker
```shell
docker run --name mysql8 -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=project-db -p 3306:3306 -d mysql:8
```

**Stop the running container**
```shell
docker stop mysql8
```

**Remove the container**
```shell
docker rm mysql8
```

### Use `docker-compose` to run the database container:
```shell
docker-compose up db -d
```

**Stop the database**
```shell
docker-compose stop db
```

**Remove the database container**
```shell
docker-compose down db
```
