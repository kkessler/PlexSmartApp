/**
 *  Plex
 *
 *  Copyright 2016 Kenan Kessler
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
	name: "Plex",
	namespace: "kkessler",
	author: "Kenan Kessler",
	description: "plex api",
	category: "Convenience",
	iconUrl: "https://www.macupdate.com/images/icons128/27302.png",
	iconX2Url: "https://www.macupdate.com/images/icons256/27302.png",
	iconX3Url: "https://www.macupdate.com/images/icons512/27302.png") {
	appSetting "URL"
	appSetting "PORT"
}


preferences {
	section("Rescan Plex") {
		input "plexToken", "text", title: "Plex Token"
		input "libraryName", "text", title: "Plex Library Name"
		input "vswitch", "capability.switch", required: false, title: "Switch"
	}
}

include 'asynchttp_v1'
import groovy.json.JsonSlurper

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
	subscribe(vswitch, "switch.on", swHandler)
	subscribe(app, appTouch)
}

def swHandler(evt) {
	log.debug("switch trigger")
	scanPlexLibrary()
	vswitch.off()
}

def appTouch(evt) {
	log.debug("appTouch trigger")
	scanPlexLibrary()
}

def scanPlexLibrary() {
	def params = [
		uri: "https://${appSettings.URL}:${appSettings.PORT}",
		path: "/library/sections",
		headers: ['X-Plex-Token' : "${plexToken}"]
	]
	asynchttp_v1.get(resphandler, params)
}

def resphandler(resp,data) {
	def libraryId = 0
	def jsonSlurper = new JsonSlurper()
	def object = jsonSlurper.parseText(resp.getData())
	log.debug "${object}"
	object["_children"].each{
		log.debug "it:${it}"
		if("${it?.title}" == libraryName){
			log.debug("found it")
			libraryId = it?._children[0]?.id
		}
	}
	log.debug("libid:${libraryId}")
	if(libraryId == 0) {
		return
	}
	def params = [
		uri: "https://${appSettings.URL}:${appSettings.PORT}",
		path: "/library/sections/${libraryId}/refresh",
		headers: ['X-Plex-Token' : "${plexToken}"]
	]
	httpGet(params)
}