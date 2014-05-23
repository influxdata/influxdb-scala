package org.influxdb.scala

import java.util.Date

case class Shard(id: Int, shortTerm: Boolean, serverIds: List[Int], startTime: Date, endTime: Date)
