package org.openrepose.filters.herp

import java.io.StringWriter
import java.net.{URL, URLDecoder}
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.{Inject, Named}
import javax.servlet._
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

import com.github.jknack.handlebars.{Options, Helper, Handlebars, Template}
import com.rackspace.httpdelegation._
import com.typesafe.scalalogging.slf4j.LazyLogging
import org.openrepose.commons.config.manager.UpdateListener
import org.openrepose.commons.utils.http.{CommonHttpHeader, OpenStackServiceHeader}
import org.openrepose.core.filter.FilterConfigHelper
import org.openrepose.core.services.config.ConfigurationService
import org.openrepose.core.spring.ReposeSpringProperties
import org.openrepose.filters.herp.config.HerpConfig
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus

import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.matching.Regex

@Named
class HerpFilter @Inject()(configurationService: ConfigurationService,
                           @Value(ReposeSpringProperties.NODE.CLUSTER_ID) clusterId: String,
                           @Value(ReposeSpringProperties.NODE.NODE_ID) nodeId: String)
                           extends Filter with HttpDelegationManager with UpdateListener[HerpConfig] with LazyLogging {
  private final val DEFAULT_CONFIG = "highly-efficient-record-processor.cfg.xml"
  private final val X_PROJECT_ID = "X-Project-ID"

  private var config: String = _
  private var initialized = false
  private var preLogger: Logger = _
  private var postLogger: Logger = _
  private var serviceCode: String = _
  private var region: String = _
  private var dataCenter: String = _
  private var handlebarsTemplate: Template = _
  private var filtersOut: Iterable[Iterable[(String, Regex)]] = _

  override def init(filterConfig: FilterConfig): Unit = {
    logger.trace("HERP filter initializing ...")
    config = new FilterConfigHelper(filterConfig).getFilterConfig(DEFAULT_CONFIG)

    logger.info("Initializing filter using config " + config)
    val xsdURL: URL = getClass.getResource("/META-INF/schema/config/highly-efficient-record-processor.xsd")
    configurationService.subscribeTo(
      filterConfig.getFilterName,
      config,
      xsdURL,
      this,
      classOf[HerpConfig]
    )

    logger.trace("HERP filter initialized.")
  }

  override def doFilter(servletRequest: ServletRequest, servletResponse: ServletResponse, filterChain: FilterChain): Unit = {
    if (!initialized) {
      logger.error("HERP filter has not yet initialized...")
      servletResponse.asInstanceOf[HttpServletResponse].sendError(500)
    } else {
      logger.trace("HERP filter passing request...")
      filterChain.doFilter(servletRequest, servletResponse)

      logger.trace("HERP filter handling response...")
      handleResponse(servletRequest.asInstanceOf[HttpServletRequest], servletResponse.asInstanceOf[HttpServletResponse])

      logger.trace("HERP filter returning response...")
    }
  }

  private def handleResponse(httpServletRequest: HttpServletRequest,
                             httpServletResponse: HttpServletResponse) = {
    def translateParameters(): Map[String, Array[String]] = {
      def decode(s: String) = URLDecoder.decode(s, StandardCharsets.UTF_8.name)

      Option(httpServletRequest.getAttribute("http://openrepose.org/queryParams")) match {
        case Some(parameters) =>
          val parametersMap = parameters.asInstanceOf[java.util.Map[String, Array[String]]].asScala
          parametersMap.map({ case (key, values) => decode(key) -> values.map(value => decode(value))}).toMap
        case None => Map[String, Array[String]]()
      }
    }

    def stripHeaderParams(headerValue: String): String = Option(headerValue).map(_.split(";", 2)(0)).orNull

    def getPreferredHeader(allProjectIds: Traversable[String]): String = {
      def getQuality(headerValue: String): Double = {
        """;\s*q\s*=\s*(\d+\.\d+)""".r.findFirstMatchIn(headerValue).map(_.group(1).toDouble).getOrElse(1.0)
      }

      if (allProjectIds.isEmpty) null
      else stripHeaderParams(allProjectIds.maxBy(getQuality))
    }

    val projectIds = httpServletRequest.getHeaders(OpenStackServiceHeader.TENANT_ID.toString).asScala
      .++(httpServletRequest.getHeaders(X_PROJECT_ID).asScala).toTraversable

    //def nullIfEmpty(it: Iterable[Any]) = if (it.isEmpty) null else it
    val eventValues: Map[String, Any] = Map(
      "userName" -> stripHeaderParams(httpServletRequest.getHeader(OpenStackServiceHeader.USER_NAME.toString)),
      "impersonatorName" -> stripHeaderParams(httpServletRequest.getHeader(OpenStackServiceHeader.IMPERSONATOR_NAME.toString)),
      "defaultProjectId" -> stripHeaderParams(getPreferredHeader(projectIds)),
      "projectID" -> projectIds.map(stripHeaderParams).toArray,
      "roles" -> httpServletRequest.getHeaders(OpenStackServiceHeader.ROLES.toString).asScala.map(stripHeaderParams).toArray,
      "userAgent" -> stripHeaderParams(httpServletRequest.getHeader(CommonHttpHeader.USER_AGENT.toString)),
      "requestMethod" -> httpServletRequest.getMethod,
      "requestURL" -> Option(httpServletRequest.getAttribute("http://openrepose.org/requestUrl")).map(_.toString).orNull,
      "targetHost" -> Option(httpServletRequest.getAttribute("http://openrepose.org/requestUrl")).map { requestUrl =>
        Try(new URL(requestUrl.toString).getHost).getOrElse(null)
      }.orNull,
      "requestQueryString" -> httpServletRequest.getQueryString,
      "parameters_SCALA" -> translateParameters(),
      "parameters" -> translateParameters().asJava.entrySet(),
      "timestamp" -> System.currentTimeMillis,
      "responseCode" -> httpServletResponse.getStatus,
      "responseMessage" -> Try(HttpStatus.valueOf(httpServletResponse.getStatus).name).getOrElse("UNKNOWN"),
      "guid" -> java.util.UUID.randomUUID.toString,
      "serviceCode" -> serviceCode,
      "region" -> region,
      "dataCenter" -> dataCenter,
      "clusterId" -> clusterId,
      "nodeId" -> nodeId,
      "requestorIp" -> Option(stripHeaderParams(httpServletRequest.getHeader(CommonHttpHeader.X_FORWARDED_FOR.toString)))
        .getOrElse(httpServletRequest.getRemoteAddr)
    )

    val templateOutput: StringWriter = new StringWriter
    handlebarsTemplate.apply(eventValues.asJava, templateOutput)

    preLogger.info(templateOutput.toString)
    if (!doFilterOut(eventValues)) {
      postLogger.info(templateOutput.toString)
    }

    def doFilterOut(valuesMap: Map[String, Any]): Boolean = {
      filtersOut.exists { andFilter =>
        andFilter.forall { case (keyOrig, pattern) =>
          val keySplit = keyOrig.split('.')
          valuesMap.get(keySplit(0)) match {
            case Some(value: Int) => pattern.findFirstIn(value.toString).isDefined
            case Some(value: Long) => pattern.findFirstIn(value.toString).isDefined
            case Some(value: String) => pattern.findFirstIn(value).isDefined
            case Some(value: Array[String]) => value.exists(pattern.findFirstIn(_).isDefined)
            case Some(value: java.util.Set[_]) =>
              // IF there is a sub key,
              // THEN try the map;
              // ELSE just bail.
              if (keySplit.size > 1) {
                // Retrieve the Scala version of the Map.
                valuesMap.get(keySplit(0) + "_SCALA") match {
                  case Some(scalaValue: Map[String, Array[String]]) =>
                    scalaValue.getOrElse(keySplit(1), Array("")).filter { s => pattern.findFirstIn(s).isDefined}.nonEmpty
                  case _ => false
                }
              } else {
                false
              }
            case _ => false
          }
        }
      }
    }
  }

  override def destroy(): Unit = {
    logger.trace("HERP filter destroying ...")
    configurationService.unsubscribeFrom(config, this.asInstanceOf[UpdateListener[_]])
    logger.trace("HERP filter destroyed.")
  }

  override def configurationUpdated(config: HerpConfig): Unit = {
    def templateString: String = {
      var templateText = config.getTemplate.getValue.trim
      if (config.getTemplate.isCrush) {
        templateText = templateText.replaceAll("(?m)[ \\t]*(\\r\\n|\\r|\\n)[ \\t]*", " ")
      }
      templateText
    }

    preLogger = LoggerFactory.getLogger(config.getPreFilterLoggerName)
    postLogger = LoggerFactory.getLogger(config.getPostFilterLoggerName)
    serviceCode = config.getServiceCode
    region = config.getRegion
    dataCenter = config.getDataCenter
    val handlebars = new Handlebars
    handlebars.registerHelper("cadfTimestamp", new CadfTimestamp)
    handlebars.registerHelper("cadfMethod", new CadfMethod)
    handlebars.registerHelper("cadfOutcome", new CadfOutcome)
    handlebarsTemplate = handlebars.compileInline(templateString)
    filtersOut = config.getFilterOut.asScala.map { filter =>
      filter.getMatch.asScala.map { matcher =>
        (matcher.getField, matcher.getRegex.r)
      }
    }
    initialized = true
  }

  override def isInitialized: Boolean = {
    initialized
  }
}

class CadfTimestamp extends Helper[Long] {
  override def apply(context: Long, options: Options): CharSequence = {
    // From http://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
    // "For formatting, if the offset value from GMT is 0, 'Z' is produced."
    // This only manipulates the Time Zones that produce a 'Z'.
    val formattedString = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date(context))
    if(formattedString.endsWith("Z")) {
      formattedString.substring(0, formattedString.length()-1) + "+00:00"
    } else {
      formattedString
    }
  }
}

class CadfMethod extends Helper[String] {
  override def apply(context: String, options: Options): CharSequence = {
    context.toLowerCase match {
      case "get"    => "read/get"
      case "head"   => "read/head"
      case "post"   => "update/post"
      case "put"    => "update/put"
      case "delete" => "update/delete"
      case "patch"  => "update/patch"
    }
  }
}

class CadfOutcome extends Helper[Int] {
  override def apply(context: Int, options: Options): CharSequence = {
    if ((context >= 200) && (context < 300))
      "success"
    else
      "failure"
  }
}
