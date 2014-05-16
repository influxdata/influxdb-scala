package org.influxdb

import org.scalatest.{ BeforeAndAfter, FunSuite }

class ClientTest  extends FunSuite with BeforeAndAfter {
	private var client: Client = null

	final val DB_NAME               = "local-test"
	final val DB_REPLICATION_FACTOR = 1
	final val DB_USER               = "user"
	final val DB_PASSWORD           = "password"
	final val CLUSTER_ADMIN_USER    = "admin"
	final val CLUSTER_ADMIN_PASS    = "pass"
	final val CLUSTER_ADMIN_NEWPASS = "new pass"


	before {
		client = new Client
	}

	after {
		client.close()
	}

	test("ping") {  
		assert(None == client.ping)
	}

	test("create|get|delete database") {
		assert(None == client.createDatabase(DB_NAME, DB_REPLICATION_FACTOR))

		val (dbs, err) = client.getDatabaseList
		assert(None == err)
		assert(Nil != dbs.filter { db => (db.name == DB_NAME) && (db.replicationFactor == DB_REPLICATION_FACTOR) })
		assert(None == client.deleteDatabase(DB_NAME))
	}

	test("create|authenticate database user") {
		assert(None == client.createDatabase(DB_NAME, DB_REPLICATION_FACTOR))
		assert(None == client.createDatabaseUser(DB_NAME, DB_USER, DB_PASSWORD))
		assert(None == client.authenticateDatabaseUser(DB_NAME, DB_USER, DB_PASSWORD))
		assert(None == client.deleteDatabase(DB_NAME))
	}

	test("create|get|update|authenticate|delete cluster admin") {
		assert(None == client.createClusterAdmin(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASS))

		val (admins, err) = client.getClusterAdminList		
		assert(None == err)
		assert(Nil != admins.filter { admin => (admin.username == CLUSTER_ADMIN_USER) })

		assert(None == client.updateClusterAdmin(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_NEWPASS))
		assert(None != client.authenticateClusterAdmin(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASS))
		assert(None == client.authenticateClusterAdmin(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_NEWPASS))
		assert(None == client.deleteClusterAdmin(CLUSTER_ADMIN_USER))
	}

	test("write|query series") {
		assert(None == client.createDatabase(DB_NAME))
		client.database = DB_NAME
		val events = Series("events", 
							Array("state", "email", "type"),
							Array(
								Array[Any]("ny", "paul@influxdb.org", "follow"),
								Array[Any]("ny", "todd@influxdb.org", "open")								
							)
					)
		val errors = Series("errors", 
							Array("class", "file", "user", "severity"),
							Array(
								Array[Any]("DivideByZero", "example.py", "someguy@influxdb.org", "fatal")	
							)
					)

		assert(None == client.writeSeries(Array(events, errors)))
		
		val (response, err) = client.query("SELECT email FROM events WHERE type = 'follow'")
		assert(None == err)

		val series = response.toSeries		
		assert(series(0).points(0)(2) == "paul@influxdb.org")
		
		val seriesMap = response.toSeriesMap
		assert(seriesMap(0).objects("email")(0) == "paul@influxdb.org")

		assert(None == client.deleteDatabase(DB_NAME))
	}

	test("get|delete continues queries") {
		assert(None == client.createDatabase(DB_NAME))
		client.database = DB_NAME

		val clicks = Series("clicks", 
							Array("ip", "value"),
							Array(
								Array("1.2.3.4", 1234),
								Array("5.6.7.8", 5678),
								Array("10.0.0.1", 10001),
								Array("127.0.0.1", 0)
							)
					)
		assert(None == client.writeSeries(Array(clicks)))

		val sql = "SELECT * FROM clicks INTO events.global"
		val (response, err1) = client.query(sql)
		assert(None == err1)

		val (queries, err2) = client.getContinuousQueries
		assert(None == err2)
		
		assert(Nil != queries.filter { q => val isEq = sql.equalsIgnoreCase(q.query)
			if (isEq) {
				assert(None == client.deleteContinuousQueries(q.id))
			}
			isEq
		})		

		assert(None == client.deleteDatabase(DB_NAME))
	}
}
