Simple experimentations with couchDB, mongoDB (and maybe more)

Run:
----

    gradle run -Ploader=elasticsearch -PnbPar=1000 -PpayloadSize=1000 -PnbMsg=200000

Cassandra installed from docker:
--------------------------------

    function cas1 { docker run --name cassandra1 -d cassandra:latest }

    function cas { docker run --name cassandra$1 -d -e CASSANDRA_SEEDS="$(docker inspect --format='{{ .NetworkSettings.IPAddress }}' cassandra1)" cassandra:latest }

ElasticSearch installed from docker:
------------------------------------

    function es { docker run -d --name es$1 -p 920$1:9200 -p 930$1:9300 -v `pwd`/src/main/resources/elasticsearch/servernode:/esconfig elasticsearch /usr/share/elasticsearch/bin/elasticsearch -Des.config=/esconfig/elasticsearch.yml }

    function esclient { docker run -d --name esc -p 9200:9200 -p 9300:9300 -v `pwd`/src/main/resources/elasticsearch/clientnode:/esconfig elasticsearch /usr/share/elasticsearch/bin/elasticsearch -Des.config=/esconfig/elasticsearch.yml }           

- launch cluster (2 data nodes, 1 client node)

    es 1; es 2; esclient


CouchDB installed from docker:
-----------------------------
docker run -d -p 5985 shykes/couchdb /bin/sh -e /usr/bin/couchdb -a /etc/couchdb/default.ini -a /etc/couchdb/local.ini -b -r 5 -p /var/run/couchdb/couchdb.pid -R

MongoDB installed from docker:
-----------------------------

    function mongo2nodes {
      docker run -p 27017:27017 --name mongo1 -h mongo1 -d mongo --replSet "repl" --storageEngine wiredTiger
      docker run -p 27027:27017 --name mongo2 -h mongo2 --link mongo1:mongo1 -d mongo --replSet "repl" --storageEngine wiredTiger
      sleep 10
      sudo sh -c 'sed -i "s/^.* mongo1/`docker inspect --format=\"{{ .NetworkSettings.IPAddress }}\" mongo1`     mongo1/g" /etc/hosts'
      docker exec mongo1 /bin/bash -c "echo '`docker inspect --format='{{ .NetworkSettings.IPAddress }}' mongo2`     mongo2' >> /etc/hosts"
      docker exec -i mongo1 /usr/bin/mongo <<EOF
        rs.initiate()
        rs.conf()
        rs.add("mongo2")
        rs.conf()
        rs.status()
EOF
    }
    function mongo3nodes {
      mongo2nodes
      docker run -p 27037:27017 --name mongo3 -h mongo3 --link mongo1:mongo1 --link mongo2:mongo2 -d mongo --replSet "repl" --storageEngine wiredTiger
      sleep 5
      docker exec mongo1 /bin/bash -c "echo '`docker inspect --format='{{ .NetworkSettings.IPAddress }}' mongo3`     mongo3' >> /etc/hosts"
      docker exec mongo2 /bin/bash -c "echo '`docker inspect --format='{{ .NetworkSettings.IPAddress }}' mongo3`     mongo3' >> /etc/hosts"
      docker exec -i mongo1 /usr/bin/mongo <<EOF
        rs.add("mongo3")
        rs.conf()
        rs.status()
EOF
    }
    function restartMongo1 {
      docker restart mongo1
      sudo sh -c 'sed -i "s/^.* mongo1/`docker inspect --format=\"{{ .NetworkSettings.IPAddress }}\" mongo1`     mongo1/g" /etc/hosts'
      docker exec mongo1 /bin/bash -c "echo '`docker inspect --format='{{ .NetworkSettings.IPAddress }}' mongo2`     mongo2' >> /etc/hosts"
      docker exec mongo1 /bin/bash -c "echo '`docker inspect --format='{{ .NetworkSettings.IPAddress }}' mongo3`     mongo3' >> /etc/hosts"
    }
