START DB:

docker run --name mysql8 -e MYSQL_ROOT_PASSWORD=scrumbags -e MYSQL_DATABASE=scrumbags-db -p 3306:3306 -d mysql:8

STOP AND CLEAN UP DB:

# To stop the running container
docker stop mysql8

# To remove the container
docker rm mysql8
