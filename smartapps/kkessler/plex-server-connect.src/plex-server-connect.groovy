/**
 *  Generic UPnP Service Manager
 *
 *  Copyright 2016 SmartThings
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
definition(
		name: "Plex Server (Connect)",
		namespace: "kkessler",
		author: "Kenan Kessler",
		description: "This service manager connects local plex servers for control by smartthings.",
		category: "SmartThings Labs",
        iconUrl: "https://www.macupdate.com/images/icons128/27302.png",
        iconX2Url: "https://www.macupdate.com/images/icons256/27302.png",
        iconX3Url: "https://www.macupdate.com/images/icons512/27302.png"
)


preferences {
	page(name: "deviceDiscovery", title: "Plex Server Device Setup", content: "deviceDiscovery")
}

def searchTarget = "urn:schemas-upnp-org:device:MediaServer:1"

def deviceDiscovery() {
    def options = [:]
    def devices = getVerifiedDevices()
    devices.each {
        def value = it.value.name ?: "Media Server ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
        def key = it.value.mac
        options["${key}"] = value
    }

    ssdpSubscribe()

    ssdpDiscover()
    verifyDevices()

    return dynamicPage(name: "deviceDiscovery", title: "Discovery Started!", nextPage: "", refreshInterval: 5, install: true, uninstall: true) {
        section("Please wait while we discover your Plex server. Discovery can take five minutes or more, so sit back and relax! Select your device below once discovered.") {
        	input "selectedDevices", "enum", required: false, title: "Select Devices (${options.size() ?: 0} found)", multiple: true, options: options
        }
    }
}

def installed() {
	log.debug "Installed with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	unsubscribe()
	unschedule()

	ssdpSubscribe()

	if (selectedDevices) {
		addDevices()
	}

	runEvery5Minutes("ssdpDiscover")
}

void ssdpDiscover() {
	log.debug("ssdpDiscover(urn:schemas-upnp-org:device:MediaServer:1)")
	sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:schemas-upnp-org:device:MediaServer:1", physicalgraph.device.Protocol.LAN))
}

void ssdpSubscribe() {
	subscribe(location, "ssdpTerm.urn:schemas-upnp-org:device:MediaServer:1", ssdpHandler)
}

Map verifiedDevices() {
	def devices = getVerifiedDevices()
	def map = [:]
	devices.each {
		def value = it.value.name ?: "Media Server ${it.value.ssdpUSN.split(':')[1][-3..-1]}"
		def key = it.value.mac
		map["${key}"] = value
	}
	map
}

void verifyDevices() {
	def devices = getDevices().findAll { it?.value?.verified != true }
	devices.each {
		int port = convertHexToInt(it.value.deviceAddress)
		String ip = convertHexToIP(it.value.networkAddress)
		String host = "${ip}:${port}"
        log.debug("VERIFYING DEVICE:GET ${host}${it.value.ssdpPath}")
		sendHubCommand(new physicalgraph.device.HubAction("""GET ${it.value.ssdpPath} HTTP/1.1\r\nHOST: $host\r\n\r\n""", physicalgraph.device.Protocol.LAN, host, [callback: deviceDescriptionHandler]))
	}
}

def getVerifiedDevices() {
	getDevices().findAll{ it.value.verified == true }
}

def getDevices() {
	if (!state.devices) {
		state.devices = [:]
	}
	state.devices
}

def addDevices() {
	log.debug("addDevices()")
    getDevices().each{ device ->
    	if(selectedDevices == device.value.mac || getChildDevice(device.value.mac) != null){
        	return
        }
    	log.debug("Creating Plex Server Device from: ${device}")
        def newdev = addChildDevice("kkessler", "Plex Server", device.value.mac, device.value.hub, [
            "label": device.value.model ?: "Plex Server",
            "data": [
                "mac": device.value.mac,
                "ip": device.value.networkAddress,
                "port": device.value.deviceAddress
            ]
        ])
        log.debug("created:${newdev}")
    }
}

def ssdpHandler(evt) {
	def description = evt.description
	def hub = evt?.hubId

	def parsedEvent = parseLanMessage(description)
	parsedEvent << ["hub":hub]

	def devices = getDevices()
	String ssdpUSN = parsedEvent.ssdpUSN.toString()
	if (devices."${ssdpUSN}") {
		def d = devices."${ssdpUSN}"
		if (d.networkAddress != parsedEvent.networkAddress || d.deviceAddress != parsedEvent.deviceAddress) {
			d.networkAddress = parsedEvent.networkAddress
			d.deviceAddress = parsedEvent.deviceAddress
			def child = getChildDevice(parsedEvent.mac)
			if (child) {
				child.sync(parsedEvent.networkAddress, parsedEvent.deviceAddress)
			}
		}
	} else {
		devices << ["${ssdpUSN}": parsedEvent]
	}
}

void deviceDescriptionHandler(physicalgraph.device.HubResponse hubResponse) {
	def body = hubResponse.xml
	def devices = getDevices()
    log.debug(devices)
	def device = devices.find { it?.key?.contains(body?.device?.UDN?.text()) }
	if (device) {
		device.value << [name: body?.device?.roomName?.text(), model:body?.device?.modelName?.text(), serialNumber:body?.device?.serialNum?.text(), verified: true]
	}
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}