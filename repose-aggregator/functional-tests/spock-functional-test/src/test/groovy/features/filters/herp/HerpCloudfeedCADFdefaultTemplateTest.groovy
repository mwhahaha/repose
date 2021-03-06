package features.filters.herp
import framework.ReposeValveTest
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Response
import spock.lang.Unroll
import java.text.SimpleDateFormat
/**
 * Created by jennyvo on 2/23/15.
 */
class HerpCloudfeedCADFdefaultTemplateTest extends ReposeValveTest {

    def setupSpec() {
        deproxy = new Deproxy()
        deproxy.addEndpoint(properties.targetPort)

        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs('features/filters/herp', params)
        repose.configurationProvider.applyConfigs('features/filters/herp/cloudfeedCADF', params)
        repose.start()
    }

    def cleanupSpec() {
        if (deproxy) {
            deproxy.shutdown()
        }

        if (repose) {
            repose.stop()
        }
    }
    @Unroll("Test filterout for Herp with method #method, username #username and origin service respCode #responseCode")
    def "Events match filterout condition will not go to post filter log"() {
        setup: "declare messageChain to be of type MessageChain"
        def Map<String, String> actionmap = [
                'GET'   : 'read/get',
                'HEAD'  : 'read/head',
                'POST'  : 'update/post',
                'PUT'   : 'update/put',
                'DELETE': 'update/delete',
                'PATCH' : 'update/patch'
        ]

        reposeLogSearch.cleanLog()
        MessageChain mc
        def Map<String, String> headers = [
                'Accept'           : 'application/xml',
                'Host'             : 'LocalHost',
                'User-agent'       : 'gdeproxy',
                'x-tenant-id'      : '123456',
                'x-roles'          : 'default',
                'x-user-name'      : username,
                'x-user-id'        : username,
                'x-impersonator-name': 'impersonateuser',
                'x-impersonator-id': '123456'
        ]
        def customHandler = { return new Response(responseCode, "Resource Not Fount", [], reqBody) }

        when:
        "When Requesting " + method + " " + request
        def curdate = new Date()
        mc = deproxy.makeRequest(url: reposeEndpoint +
                request, method: method, headers: headers,
                requestBody: reqBody, defaultHandler: customHandler,
                addDefaultHeaders: false
        )
        def format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        def now = format.format(curdate)
        // From http://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html
        // "For formatting, if the offset value from GMT is 0offset value from GMT is 0, 'Z' is produced."
        // This only manipulates the Time Zones that produce a 'Z'.
        if(now.endsWith("Z")) {
            now.substring(0, now.length()-1) + "+00:00"
        }

        String logLine = reposeLogSearch.searchByString("INFO  org.openrepose.herp.pre.filter")
        String eventxml = logLine.substring(logLine.indexOf("<?xml"),logLine.size() - 1)
        println (eventxml)
        def event = new XmlSlurper().parseText(eventxml).declareNamespace(au:"http://feeds.api.rackspacecloud.com/cadf/user-access-event")

        then:
        "result should be " + responseCode
        mc.receivedResponse.code.equals(responseCode)
        event.@eventType.text() == "activity"
        event.@typeURI.text() == "http://schemas.dmtf.org/cloud/audit/1.0/event"
        //event.@eventTime.text() == now
        now.compareTo(event.@eventTime.text()) <= 0
        event.@action.text() == actionmap.get(method)
        event.@outcome.text() == 'failure'
        //event.initiator.@name.text() == "impersonateuser"
        event.initiator.host.@address.text() == "127.0.0.1"
        event.initiator.host.@agent.text() == "gdeproxy"
        event.target.@name.text() == "repose"
        // check auditData
        event.attachments.attachment.@name.text() == "auditData"
        event.attachments.attachment.content."au:auditData"."au:region" == "USA"
        event.attachments.attachment.content."au:auditData"."au:dataCenter" == "DFW"
        event.attachments.attachment.content."au:auditData"."au:methodLabel" == "TODO"  // to be changed
        (event.attachments.attachment.content."au:auditData"."au:requestURL".toString()).contains(request)
        event.attachments.attachment.content."au:auditData"."au:queryString" == ""
        event.attachments.attachment.content."au:auditData"."au:tenantId" == "123456"
        event.attachments.attachment.content."au:auditData"."au:responseMessage" == respMsg
        event.attachments.attachment.content."au:auditData"."au:userName" == username
        event.attachments.attachment.content."au:auditData"."au:roles" == "default"

        where:
        responseCode | username     | request                      | method  | reqBody     | respMsg
        "404"        | "User"       | "/resource1/id/aaaaaaaaaaaa" | "GET"   | ""          | "NOT_FOUND"
        "405"        | "testUser"   | "/resource1/id"              | "POST"  | ""          | "METHOD_NOT_ALLOWED"
        "400"        | "reposeUser" | "/resource1/id/cccccccccccc" | "PUT"   | "some data" | "BAD_REQUEST"
        "415"        | "reposeUser1"| "/resource1/id/dddddddddddd" | "PATCH" | "some data" | "UNSUPPORTED_MEDIA_TYPE"
        "413"        | "reposeTest" | "/resource1/id/eeeeeeeeeeee" | "PUT"   | "some data" | "PAYLOAD_TOO_LARGE"
        "500"        | "reposeTest1"| "/resource1/id/ffffffffffff" | "PUT"   | "some data" | "INTERNAL_SERVER_ERROR"
    }
}
