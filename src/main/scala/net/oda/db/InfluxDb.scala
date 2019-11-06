package net.oda.db

import com.paulgoldbaum.influxdbclient.InfluxDB
import net.oda.Config.props

import scala.concurrent.ExecutionContext.Implicits.global

object InfluxDb {
  private val server = InfluxDB.connect(props.influxdb.host, props.influxdb.port)
  val db = server.selectDatabase(props.influxdb.db)
}
