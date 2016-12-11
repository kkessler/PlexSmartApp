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
        capability "Media Controller"
        
        attribute "currentActivity", "String"
        
        command "scanAllLibraries"
	}

	simulator {

	}
	tiles {
 		standardTile("scanAllLibraries", "device.button", width: 1, height: 1, decoration: "flat") {
			state "default", label: "Scan All", backgroundColor: "#ffffff", action: "scanAllLibraries"
		}
		main "scanAllLibraries"
		details(["scanAllLibraries"])
	}
}

def parse(String description) {
    def msg = parseLanMessage(description)
    if(msg.body != null){
  		refreshLibraries(msg.json)
    }
}

physicalgraph.device.HubAction scanAllLibraries() {
	sendEvent(name: "button", value: "pushed", data: [buttonNumber: "1"], descriptionText: "$device.displayName Scan All Libraries was activated", isStateChange: true)
    return new physicalgraph.device.HubAction(
        method: "GET",
        path: "/library/sections",
        headers: [
            HOST: getHostAddress(),
            'Accept': 'application/json'
        ]
	)
}

//refresh all the libraries that are returned in the json from /library/sections/
def refreshLibraries(HashMap msg){
	int libraryId=0
    msg["_children"].each{
        	libraryId = it?._children[0]?.id
    		if(libraryId == 0 || libraryId == null){
            	return
            }
            log.debug("refreshing ${it?.title}")
            sendHubCommand( new physicalgraph.device.HubAction(
                method: "GET",
                path: "/library/sections/${libraryId}/refresh",
                headers: [
                    HOST: getHostAddress(),
                    'Accept': 'application/json'
                ]
            ))
            libraryId=0
    }
}

// gets the address of the device
private String getHostAddress() {
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

	// this is a different port than discovered, so override here to the deault port.
    return convertHexToIP(ip) + ":32400"
}

private Integer convertHexToInt(hex) {
    return Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
    return [convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}