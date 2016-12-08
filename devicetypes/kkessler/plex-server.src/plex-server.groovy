/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Plex Server", namespace: "kkessler", author: "Kenan Kessler") {
		capability "Actuator"
		capability "Button"
		capability "Sensor"
        
        command "push"
	}

	simulator {

	}
	tiles {
 		standardTile("push", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Scan", backgroundColor: "#ffffff", action: "push"
		} 
		main "button"
		details(["button","push"])
	}
}
import groovy.json.JsonSlurper

def parse(String description) {
	log.debug("d:${description}")
    def msg = parseLanMessage(description)
    log.debug("m:${msg}")
}

def push() {
	sendEvent(name: "button", value: "pushed", data: [buttonNumber: "1"], descriptionText: "$device.displayName button 1 was pushed", isStateChange: true)
    return new physicalgraph.device.HubAction(
        method: "GET",
        path: "/library/sections",
        headers: [
            HOST: getHostAddress()
        ]
	)
}

def resphandler(resp,data) {
    def libraryId = 0
    def jsonSlurper = new JsonSlurper()
    def object = jsonSlurper.parseText(resp.getData())
    object["_children"].each{
		if("${it?.title}" == libraryName){
        	libraryId = it?._children[0]?.id
        }
    }
    log.debug("libid:${libraryId}")
    if(libraryId == 0){
    	return
    }
    def params = [
        uri: "https://${appSettings.URL}:${appSettings.PORT}",
        path: "/library/sections/${libraryId}/refresh",
        headers: ['X-Plex-Token' : "${plexToken}"]
    ]
    httpGet(params)
}

// gets the address of the hub
private getCallBackAddress() {
    return device.hub.getDataValue("localIP") + ":" + device.hub.getDataValue("localSrvPortTCP")
}

// gets the address of the device
private getHostAddress() {
    def ip = getDataValue("ip")
    def port = getDataValue("port")

    if (!ip || !port) {
        def parts = device.deviceNetworkId.split(":")
        if (parts.length == 2) {
            ip = parts[0]
            port = parts[1]
        } else {
            log.warn "Can't figure out ip and port for device: ${device.id}"
        }
    }

    log.debug "Using IP: $ip and port: $port for device: ${device.id}"
    return convertHexToIP(ip) + ":" + convertHexToInt(port)
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}