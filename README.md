Simple experimentations with couchDB, mongoDB (and maybe more)

ElasticSearch installed from docker:
------------------------------------

function es { docker run -d --name es$1 -p 920$1:9200 -p 930$1:9300 -v `pwd`src/main/resources/elasticsearch/servernode:/esconfig elasticsearch /usr/share/elasticsearch/bin/elasticsearch -Des.config=/esconfig/elasticsearch.yml }

function esclient { docker run -d --name esc -p 9200:9200 -p 9300:9300 -v `pwd`src/main/resources/elasticsearch/clientnode:/esconfig elasticsearch /usr/share/elasticsearch/bin/elasticsearch -Des.config=/esconfig/elasticsearch.yml }           

# launch cluster (2 data nodes, 1 client node)
es 1; es 2; esclient


couchDB installed from docker:
-----------------------------
docker run -d -p 5985 shykes/couchdb /bin/sh -e /usr/bin/couchdb -a /etc/couchdb/default.ini -a /etc/couchdb/local.ini -b -r 5 -p /var/run/couchdb/couchdb.pid -R

mongoDB installed from docker:
-----------------------------
docker run -d -p 27017:27017 --name mongodb dockerfile/mongodb
