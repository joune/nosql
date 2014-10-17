Simple experimentations with couchDB, mongoDB (and maybe more)

couchDB installed from docker:
-----------------------------
docker run -d -p 5985 shykes/couchdb /bin/sh -e /usr/bin/couchdb -a /etc/couchdb/default.ini -a /etc/couchdb/local.ini -b -r 5 -p /var/run/couchdb/couchdb.pid -R

mongoDB installed from docker:
-----------------------------
docker run -d -p 27017:27017 --name mongodb dockerfile/mongodb
