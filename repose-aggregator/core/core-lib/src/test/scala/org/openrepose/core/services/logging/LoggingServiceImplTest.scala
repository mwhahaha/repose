package org.openrepose.core.services.logging

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.config.ConfigurationFactory
import org.apache.logging.log4j.status.StatusLogger
import org.apache.logging.log4j.test.appender.ListAppender
import org.junit.runner.RunWith
import org.openrepose.core.services.config.ConfigurationService
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FunSpec, Matchers}
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source

@RunWith(classOf[JUnitRunner])
class LoggingServiceImplTest extends FunSpec with Matchers with MockitoSugar{

  import scala.collection.JavaConversions._

  private val LOG: Logger = LoggerFactory.getLogger(classOf[LoggingServiceImplTest].getName)

  val mockConfigService = mock[ConfigurationService]

  /**
   * This is here because the yaml backend in Log4j2 is currently borked.
   * When the tests suddenly start failing, that means that we'll need to just remove the yamlWrapper
   * around the test code and see if the tests pass. Until that time they'll be ignored.
   * @param extension the file extension
   * @param f the function to wrap
   * @return
   */
  def yamlWrapper(extension: String)(f: => Unit) = {
    if (extension == "yaml") {
      pendingUntilFixed(f)
    } else {
      f
    }
  }

  def cleanupLoggerContext(context: LoggerContext)() = {
    System.clearProperty(ConfigurationFactory.CONFIGURATION_FILE_PROPERTY)
    context.reconfigure
    StatusLogger.getLogger.reset
  }

  val validExtensions = List("xml", "json", "yaml")
  validExtensions.foreach { ext =>
    describe(s"On Startup with extension $ext, the system") {
      it("Loads a fully qualified URL.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content1 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List1.$ext")).mkString
            val file1 = File.createTempFile("log4j2-", s".$ext")
            file1.deleteOnExit()
            Files.write(file1.toPath, content1.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file1.toURI.toURL.toString)

            val config1 = context.getConfiguration
            val app1 = config1.getAppender("List1").asInstanceOf[ListAppender]
            app1 should not be null
            app1.clear

            LOG.error("ERROR LEVEL LOG STATEMENT 1")
            LOG.warn("WARN  LEVEL LOG STATEMENT 1")
            LOG.info("INFO  LEVEL LOG STATEMENT 1")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 1")
            LOG.trace("TRACE LEVEL LOG STATEMENT 1")

            val events1 = app1.getEvents.toList.map(_.getMessage.getFormattedMessage)

            events1 should contain("ERROR LEVEL LOG STATEMENT 1")
            events1 should contain("WARN  LEVEL LOG STATEMENT 1")
            events1 should not contain "INFO  LEVEL LOG STATEMENT 1"
            events1 should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events1 should not contain "TRACE LEVEL LOG STATEMENT 1"
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }

      it("Loads a relative pathed file.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content1 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List1.$ext")).mkString
            val file1 = File.createTempFile("log4j2-", s".$ext")
            file1.deleteOnExit()
            Files.write(file1.toPath, content1.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file1.getCanonicalPath)

            val config1 = context.getConfiguration
            val app1 = config1.getAppender("List1").asInstanceOf[ListAppender]
            app1 should not be null
            app1.clear

            LOG.error("ERROR LEVEL LOG STATEMENT 1")
            LOG.warn("WARN  LEVEL LOG STATEMENT 1")
            LOG.info("INFO  LEVEL LOG STATEMENT 1")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 1")
            LOG.trace("TRACE LEVEL LOG STATEMENT 1")

            val events = app1.getEvents.toList.map(_.getMessage.getFormattedMessage)

            events should contain("ERROR LEVEL LOG STATEMENT 1")
            events should contain("WARN  LEVEL LOG STATEMENT 1")
            events should not contain "INFO  LEVEL LOG STATEMENT 1"
            events should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events should not contain "TRACE LEVEL LOG STATEMENT 1"
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }

      it("Falls back to known good configuration on failure.") {
        val loggingService = new LoggingServiceImpl(mockConfigService)
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]

        loggingService.updateLoggingConfiguration(s"BAD_FILE_NAME.$ext")

        val config1 = context.getConfiguration
        val appDefault = config1.getAppender("ListDefault").asInstanceOf[ListAppender]
        appDefault should not be null

        val events = appDefault.getEvents.toList.map(_.getMessage.getFormattedMessage)
        events.find(_.contains("ERROR FALLING BACK TO THE DEFAULT LOGGING CONFIGURATION!!!")) should not be None

        cleanupLoggerContext(context)
      }
    }

    describe(s"From know good state with extension $ext, the system ") {
      it("Switches to another known configuration.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content1 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List1.$ext")).mkString
            val file1 = File.createTempFile("log4j2-", s".$ext")
            file1.deleteOnExit()
            Files.write(file1.toPath, content1.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file1.toURI.toURL.toString)

            val config1 = context.getConfiguration
            val app1 = config1.getAppender("List1").asInstanceOf[ListAppender]
            app1 should not be null
            app1.clear

            LOG.error("ERROR LEVEL LOG STATEMENT 1")
            LOG.warn("WARN  LEVEL LOG STATEMENT 1")
            LOG.info("INFO  LEVEL LOG STATEMENT 1")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 1")
            LOG.trace("TRACE LEVEL LOG STATEMENT 1")

            val content2 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List2.$ext")).mkString
            val file2 = File.createTempFile("log4j2-", s".$ext")
            file2.deleteOnExit()
            Files.write(file2.toPath, content2.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file2.toURI.toURL.toString)

            LOG.error("ERROR LEVEL LOG STATEMENT 2")
            LOG.warn("WARN  LEVEL LOG STATEMENT 2")
            LOG.info("INFO  LEVEL LOG STATEMENT 2")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 2")
            LOG.trace("TRACE LEVEL LOG STATEMENT 2")

            val events1 = app1.getEvents.toList.map(_.getMessage.getFormattedMessage)
            events1 should contain("ERROR LEVEL LOG STATEMENT 1")
            events1 should contain("WARN  LEVEL LOG STATEMENT 1")
            events1 should not contain "INFO  LEVEL LOG STATEMENT 1"
            events1 should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events1 should not contain "TRACE LEVEL LOG STATEMENT 1"
            events1 should not contain "ERROR LEVEL LOG STATEMENT 2"
            events1 should not contain "WARN  LEVEL LOG STATEMENT 2"
            events1 should not contain "INFO  LEVEL LOG STATEMENT 2"
            events1 should not contain "DEBUG LEVEL LOG STATEMENT 2"
            events1 should not contain "TRACE LEVEL LOG STATEMENT 2"

            val config2 = context.getConfiguration
            val app2 = config2.getAppender("List2").asInstanceOf[ListAppender]
            app2 should not be null
            val events2 = app2.getEvents.toList.map(_.getMessage.getFormattedMessage)
            events2 should not contain "ERROR LEVEL LOG STATEMENT 1"
            events2 should not contain "WARN  LEVEL LOG STATEMENT 1"
            events2 should not contain "INFO  LEVEL LOG STATEMENT 1"
            events2 should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events2 should not contain "TRACE LEVEL LOG STATEMENT 1"
            events2 should contain("ERROR LEVEL LOG STATEMENT 2")
            events2 should contain("WARN  LEVEL LOG STATEMENT 2")
            events2 should contain("INFO  LEVEL LOG STATEMENT 2")
            events2 should contain("DEBUG LEVEL LOG STATEMENT 2")
            events2 should not contain "TRACE LEVEL LOG STATEMENT 2"
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }

      it("Fails to switch when provided NULL.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content2 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List2.$ext")).mkString
            val file2 = File.createTempFile("log4j2-", s".$ext")
            file2.deleteOnExit()
            Files.write(file2.toPath, content2.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file2.getAbsolutePath)

            val config2 = context.getConfiguration
            val app2 = config2.getAppender("List2").asInstanceOf[ListAppender]
            app2 should not be null
            app2.clear

            loggingService.updateLoggingConfiguration(null)

            val events2 = app2.getEvents.toList.map(_.getMessage.getFormattedMessage)
            events2 should contain("Requested to reload a NULL configuration.")
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }

      it("Fails to switch when provided the same file.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content2 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List2.$ext")).mkString
            val file2 = File.createTempFile("log4j2-", s".$ext")
            file2.deleteOnExit()
            Files.write(file2.toPath, content2.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file2.getAbsolutePath)

            val config2 = context.getConfiguration
            val app2 = config2.getAppender("List2").asInstanceOf[ListAppender]
            app2 should not be null
            app2.clear

            loggingService.updateLoggingConfiguration(file2.getAbsolutePath)

            val events2 = app2.getEvents.toList.map(_.getMessage.getFormattedMessage)

            events2 should contain("Requested to reload the same configuration: " + file2.getAbsolutePath)
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }

      val invalidUrls = List(s"http://www.example.com/log4j2.$ext",
        s"https://www.example.com/log4j2.$ext",
        s"ftp://www.example.com/log4j2.$ext",
        s"jar://log4j2.$ext",
        s"classpath:///log4j2.$ext"
      )
      invalidUrls.foreach { url =>
        it( s"""Fails to switch when provided the invalid URL: "$url".""") {
          val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
          try {
            yamlWrapper(ext) {
              val loggingService = new LoggingServiceImpl(mockConfigService)

              val content1 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List1.$ext")).mkString
              val file1 = File.createTempFile("log4j2-", s".$ext")
              file1.deleteOnExit()
              Files.write(file1.toPath, content1.getBytes(StandardCharsets.UTF_8))

              loggingService.updateLoggingConfiguration(file1.toURI.toURL.toString)

              val config = context.getConfiguration
              val app1 = config.getAppender("List1").asInstanceOf[ListAppender]
              app1 should not be null
              app1.clear

              loggingService.updateLoggingConfiguration(url)

              val events1 = app1.getEvents.toList.map(_.getMessage.getFormattedMessage)

              events1 should contain(s"An attempt was made to switch to an invalid Logging Configuration file: $url")
            }
          } finally {
            cleanupLoggerContext(context)
          }
        }
      }
    }

    describe(s"On configuration modification with extension $ext, the system") {
      it("reloads the configuration.") {
        val context = LogManager.getContext(false).asInstanceOf[LoggerContext]
        try {
          yamlWrapper(ext) {
            val loggingService = new LoggingServiceImpl(mockConfigService)

            val content1 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List1.$ext")).mkString
            val file1 = File.createTempFile("log4j2-", s".$ext")
            file1.deleteOnExit()
            Files.write(file1.toPath, content1.getBytes(StandardCharsets.UTF_8))

            loggingService.updateLoggingConfiguration(file1.toURI.toURL.toString)

            val config1 = context.getConfiguration
            val app1 = config1.getAppender("List1").asInstanceOf[ListAppender]
            app1 should not be null
            app1.clear

            LOG.error("ERROR LEVEL LOG STATEMENT 1")
            LOG.warn("WARN  LEVEL LOG STATEMENT 1")
            LOG.info("INFO  LEVEL LOG STATEMENT 1")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 1")
            LOG.trace("TRACE LEVEL LOG STATEMENT 1")

            var i = 5 + 1
            LOG.debug(s"Waiting for one second longer than the monitor interval ($i)..")
            System.out.print("Waiting for one second longer than the monitor interval..")
            while (i > 0) {
              System.out.print(". ")
              Thread.sleep(1000)
              i -= 1
            }
            System.out.println(".")

            val content2 = Source.fromInputStream(this.getClass.getResourceAsStream(s"/LoggingServiceImplTest/log4j2-List2.$ext")).mkString
            Files.write(file1.toPath, content2.getBytes(StandardCharsets.UTF_8))

            // This loop and sleep was adopted directly from the Log4J 2.x tests
            // provides the tickling of the logging infrastructure to wake up and reload.
            var j = 15
            while (j > 0) {
              LOG.trace(s"Reconfiguring LogManager... $j")
              j -= 1
            }
            Thread.sleep(100)

            LOG.error("ERROR LEVEL LOG STATEMENT 2")
            LOG.warn("WARN  LEVEL LOG STATEMENT 2")
            LOG.info("INFO  LEVEL LOG STATEMENT 2")
            LOG.debug("DEBUG LEVEL LOG STATEMENT 2")
            LOG.trace("TRACE LEVEL LOG STATEMENT 2")

            val events1 = app1.getEvents.toList.map(_.getMessage.getFormattedMessage)
            events1 should contain("ERROR LEVEL LOG STATEMENT 1")
            events1 should contain("WARN  LEVEL LOG STATEMENT 1")
            events1 should not contain "INFO  LEVEL LOG STATEMENT 1"
            events1 should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events1 should not contain "TRACE LEVEL LOG STATEMENT 1"
            events1 should not contain "ERROR LEVEL LOG STATEMENT 2"
            events1 should not contain "WARN  LEVEL LOG STATEMENT 2"
            events1 should not contain "INFO  LEVEL LOG STATEMENT 2"
            events1 should not contain "DEBUG LEVEL LOG STATEMENT 2"
            events1 should not contain "TRACE LEVEL LOG STATEMENT 2"

            val config2 = context.getConfiguration
            val app2 = config2.getAppender("List2").asInstanceOf[ListAppender]
            app2 should not be null
            val events2 = app2.getEvents.toList.map(_.getMessage.getFormattedMessage)
            events2 should not contain "ERROR LEVEL LOG STATEMENT 1"
            events2 should not contain "WARN  LEVEL LOG STATEMENT 1"
            events2 should not contain "INFO  LEVEL LOG STATEMENT 1"
            events2 should not contain "DEBUG LEVEL LOG STATEMENT 1"
            events2 should not contain "TRACE LEVEL LOG STATEMENT 1"
            events2 should contain("ERROR LEVEL LOG STATEMENT 2")
            events2 should contain("WARN  LEVEL LOG STATEMENT 2")
            events2 should contain("INFO  LEVEL LOG STATEMENT 2")
            events2 should contain("DEBUG LEVEL LOG STATEMENT 2")
            events2 should not contain "TRACE LEVEL LOG STATEMENT 2"
          }
        } finally {
          cleanupLoggerContext(context)
        }
      }
    }
  }
}
