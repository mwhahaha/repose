package features.filters.derp

import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import org.joda.time.DateTime
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import org.rackspace.deproxy.Response
import spock.lang.Unroll

/**
 * Created by jamesc on 12/2/14.
 */
class DerpAndDelegableQuality extends ReposeValveTest {

    def static originEndpoint
    def static identityEndpoint

    def static MockIdentityService fakeIdentityService

    def setupSpec() {

        deproxy = new Deproxy()

        def params = properties.defaultTemplateParams
        repose.configurationProvider.applyConfigs("common", params)
        repose.configurationProvider.applyConfigs("features/filters/derp/responsemessaging/delegableQuality", params)
        repose.start(waitOnJmxAfterStarting: false)
        repose.waitForNon500FromUrl(reposeEndpoint)

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort,
                'identity service', null, fakeIdentityService.handler)

    }

    def cleanupSpec() {
        if(deproxy)
            deproxy.shutdown()
        if(repose)
            repose.stop()
    }

    def setup(){
        fakeIdentityService.resetHandlers()
    }

    /*
        This test to verify that discrete quality values for 2 delegable filters processed correctly by the
        DeRP filter.
    */

    @Unroll("req method: #method, #path, #roles")
    def "when req without token, non tenanted and delegable mode (2) with quality"() {
        given:
        fakeIdentityService.with {
            client_token = ""
            tokenExpiresAt = (new DateTime()).plusDays(1);
            service_admin_role = "non-admin"
        }
        Map<String, String> headers = ["X-Roles" : roles,
                                       "Content-Type" : "application/xml",
                                       "X-Auth-Token": fakeIdentityService.client_token]

        when: "User passes a request through repose with authN and apiValidator delegable"
        MessageChain mc = deproxy.makeRequest(
                url: "$reposeEndpoint/$path",
                method: method,
                headers: headers)

        then: "Request body sent from repose to the origin service should contain"
        mc.receivedResponse.code == responseCode
        mc.receivedResponse.body.contains(msgBody)
        mc.handlings.size() == 0


        where:
        method  | path           | roles                 | responseCode   | msgBody                     | component       | quality
        "GET"   | "servers/"     |"raxRole"              | "403"          | "forbidden"                 | "api-checker"   | 0.6
        "GET"   | "servers/"     |"raxRole, a:observer"  | "401"          | "Failure in Auth-N filter." | "client-auth-n" | 0.3
        "POST"  | "servers/1235" |"raxRole, a:observer"  | "404"          | "Resource not found"        | "api-checker"   | 0.6
        "PUT"   | "servers/"     |"raxRole, a:admin"     | "405"          | "Bad method"                | "api-checker"   | 0.6
        "DELETE"| "servers/test" |"raxRole, a:observer"  | "404"          | "Resource not found"        | "api-checker"   | 0.6
        "GET"   | "get/"         |"raxRole"              | "404"          | "Resource not found"        | "api-checker"   | 0.6

    }

}

