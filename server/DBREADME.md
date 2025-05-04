START DB:

docker run --name mysql8 -e MYSQL_ROOT_PASSWORD=scrumbags -e MYSQL_DATABASE=scrumbags-db -p 3306:3306 -d mysql:8
