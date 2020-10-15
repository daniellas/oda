package net.oda.influx

import com.paulgoldbaum.influxdbclient.{HttpConfig, InfluxDB}
import net.oda.Config.props

import scala.concurrent.ExecutionContext.Implicits.global

object InfluxDb {
  private val server = InfluxDB.connect(
    props.influxdb.host,
    props.influxdb.port,
    null,
    null,
    false,
    new HttpConfig()
      .setConnectTimeout(60000)
      .setRequestTimeout(60000)
  )
  val db = server.selectDatabase(props.influxdb.db)
}
