package net.oda.influx

import com.paulgoldbaum.influxdbclient.{HttpConfig, InfluxDB}
import net.oda.Config.props

import scala.concurrent.ExecutionContext.Implicits.global

object Influx {
  private val server = InfluxDB.connect(
    props.influxdb.host,
    props.influxdb.port,
    null,
    null,
    false,
    new HttpConfig()
      .setConnectTimeout(props.influxdb.connectTimeout)
      .setRequestTimeout(props.influxdb.receiveTimeout)
  )
  val db = server.selectDatabase(props.influxdb.db)
}
